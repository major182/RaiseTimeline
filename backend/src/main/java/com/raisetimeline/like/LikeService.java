package com.raisetimeline.like;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.post.PostRepository;
import com.raisetimeline.user.UserSummaries;
import com.raisetimeline.user.UserSummary;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** いいねの業務ルール（要件定義書 BR-32〜36、API 設計書 4.6）。 */
@Service
public class LikeService {

    /** 続きがあるかを知るため、1 件多く取る（{@link Cursors#page}）。 */
    private static final Limit PAGE_PLUS_ONE = Limit.of(Cursors.PAGE_SIZE + 1);

    private final LikeRepository likes;
    private final PostRepository posts;
    private final UserSummaries summaries;
    private final Cursors cursors;

    public LikeService(LikeRepository likes, PostRepository posts, UserSummaries summaries, Cursors cursors) {
        this.likes = likes;
        this.posts = posts;
        this.summaries = summaries;
        this.cursors = cursors;
    }

    /** いいねする（F-LK-01）。いいね済みでも、何も変えずに今の状態を返す（A-4）。 */
    @Transactional
    public LikeResponse like(long meId, long postId) {
        requirePost(postId);
        likes.insertIfAbsent(postId, meId);
        return new LikeResponse(true, likes.countByPostId(postId));
    }

    /** いいねを取り消す（F-LK-01）。いいねしていなくても、今の状態を返す（A-4）。 */
    @Transactional
    public LikeResponse unlike(long meId, long postId) {
        requirePost(postId);
        likes.deleteByPair(postId, meId);
        return new LikeResponse(false, likes.countByPostId(postId));
    }

    /** いいねした人の一覧（F-LK-03）。いいねした時刻の新しい順。 */
    @Transactional(readOnly = true)
    public CursorPage<UserSummary> likers(long postId, String cursor, long meId) {
        requirePost(postId);
        LikeCursor after = cursors.decode(cursor, LikeCursor.class);
        List<Like> rows = after == null
                ? likes.findByPost(postId, PAGE_PLUS_ONE)
                : likes.findByPostAfter(postId, after.at(), after.id(), PAGE_PLUS_ONE);
        return cursors.page(
                rows,
                l -> new LikeCursor(l.getCreatedAt(), l.getUserId()),
                pageRows -> summaries.ofIds(pageRows.stream().map(Like::getUserId).toList(), meId));
    }

    private void requirePost(long postId) {
        if (!posts.existsById(postId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
