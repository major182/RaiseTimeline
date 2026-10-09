package com.raisetimeline.follow;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.user.UserRepository;
import com.raisetimeline.user.UserSummaries;
import com.raisetimeline.user.UserSummary;
import java.util.List;
import java.util.function.ToLongFunction;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** フォローの業務ルール（要件定義書 BR-42〜44、API 設計書 4.7）。 */
@Service
public class FollowService {

    /** 続きがあるかを知るため、1 件多く取る（{@link Cursors#page}）。 */
    private static final Limit PAGE_PLUS_ONE = Limit.of(Cursors.PAGE_SIZE + 1);

    private final FollowRepository follows;
    private final UserRepository users;
    private final UserSummaries summaries;
    private final Cursors cursors;

    public FollowService(FollowRepository follows, UserRepository users, UserSummaries summaries, Cursors cursors) {
        this.follows = follows;
        this.users = users;
        this.summaries = summaries;
        this.cursors = cursors;
    }

    /** フォローする。何度呼んでも結果は同じ（A-4）。自分自身はフォローできない（BR-43）。 */
    @Transactional
    public void follow(long meId, long userId) {
        if (meId == userId) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        requireUser(userId);
        follows.insertIfAbsent(meId, userId);
    }

    /** フォローをやめる。フォローしていなくてもエラーにしない（A-4）。 */
    @Transactional
    public void unfollow(long meId, long userId) {
        requireUser(userId);
        follows.deleteByPair(meId, userId);
    }

    /** userId がフォローしている人の一覧（F-FL-02）。フォローした時刻の新しい順。 */
    @Transactional(readOnly = true)
    public CursorPage<UserSummary> following(long userId, String cursor, long meId) {
        requireUser(userId);
        FollowCursor after = cursors.decode(cursor, FollowCursor.class);
        List<Follow> rows = after == null
                ? follows.findFollowing(userId, PAGE_PLUS_ONE)
                : follows.findFollowingAfter(userId, after.at(), after.id(), PAGE_PLUS_ONE);
        return toPage(rows, Follow::getFolloweeId, meId);
    }

    /** userId をフォローしている人の一覧（F-FL-03）。フォローされた時刻の新しい順。 */
    @Transactional(readOnly = true)
    public CursorPage<UserSummary> followers(long userId, String cursor, long meId) {
        requireUser(userId);
        FollowCursor after = cursors.decode(cursor, FollowCursor.class);
        List<Follow> rows = after == null
                ? follows.findFollowers(userId, PAGE_PLUS_ONE)
                : follows.findFollowersAfter(userId, after.at(), after.id(), PAGE_PLUS_ONE);
        return toPage(rows, Follow::getFollowerId, meId);
    }

    /** フォローの行を、一覧に出す相手の利用者の要約に変える。 */
    private CursorPage<UserSummary> toPage(List<Follow> rows, ToLongFunction<Follow> userIdOf, long meId) {
        return cursors.page(
                rows,
                f -> new FollowCursor(f.getCreatedAt(), userIdOf.applyAsLong(f)),
                pageRows -> summaries.ofIds(pageRows.stream().map(userIdOf::applyAsLong).toList(), meId));
    }

    private void requireUser(long userId) {
        if (!users.existsById(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
