package com.raisetimeline.user;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.error.FieldErrorCode;
import com.raisetimeline.common.error.FieldErrorDetail;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.follow.FollowRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 利用者・プロフィールの業務ルール（要件定義書 BR-02・03・25・44、API 設計書 4.2）。 */
@Service
public class UserService {

    /** 検索語の長さ（DB 設計書 5.6）。 */
    static final int QUERY_MAX_LENGTH = 50;

    /** おすすめの利用者の人数（F-US-04）。 */
    static final int RECOMMENDATION_COUNT = 5;

    private final UserRepository users;
    private final FollowRepository follows;
    private final UserSummaries summaries;
    private final Cursors cursors;

    public UserService(UserRepository users, FollowRepository follows, UserSummaries summaries, Cursors cursors) {
        this.users = users;
        this.follows = follows;
        this.summaries = summaries;
        this.cursors = cursors;
    }

    /** ID でプロフィールを取る（F-US-01）。 */
    @Transactional(readOnly = true)
    public ProfileResponse profile(long userId, long meId) {
        return toProfile(users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)), meId);
    }

    /** ユーザー名でプロフィールを取る（画面の URL /users/{ユーザー名} から開くとき）。大文字・小文字を区別しない。 */
    @Transactional(readOnly = true)
    public ProfileResponse profileByUsername(String username, long meId) {
        return toProfile(
                users.findByUsername(username).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)), meId);
    }

    /**
     * プロフィールを変える（F-US-02）。入力の形式は、呼ぶ前に Bean Validation で確かめてある。
     * ユーザー名が自分以外に使われていれば 409。自分の今のユーザー名（大文字・小文字だけの変更を含む）は使える。
     */
    @Transactional
    public ProfileResponse updateProfile(long meId, UpdateProfileRequest request) {
        User me = users.findById(meId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (request.username() != null && users.existsByUsernameExcept(request.username(), meId)) {
            throw usernameTaken();
        }
        me.updateProfile(request.displayName(), request.username(), request.bio());
        try {
            users.saveAndFlush(me);
        } catch (DataIntegrityViolationException e) {
            // 確かめた直後に、同じ名前で別の人が登録・変更した場合。DB の UNIQUE 索引が最後の安全網になる
            throw usernameTaken();
        }
        return toProfile(me, meId);
    }

    /** 利用者の検索（F-US-03、DB 設計書 5.6）。20 件ずつ。 */
    @Transactional(readOnly = true)
    public CursorPage<UserSummary> search(String q, String cursor, long meId) {
        String query = q == null ? "" : q.strip();
        int length = query.codePointCount(0, query.length());
        if (length < 1 || length > QUERY_MAX_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, List.of(FieldErrorDetail.of("q", FieldErrorCode.QUERY_LENGTH)));
        }
        SearchCursor after = cursors.decode(cursor, SearchCursor.class);
        int offset = after == null ? 0 : after.offset();
        List<User> rows = users.search(escapeLike(query), query, Cursors.PAGE_SIZE + 1, offset);
        return cursors.page(
                rows, u -> new SearchCursor(offset + Cursors.PAGE_SIZE), pageRows -> summaries.of(pageRows, meId));
    }

    /**
     * おすすめの利用者（F-US-04、DB 設計書 5.7）。最大 5 人。
     * 「フォロー中の人がフォローしている人」で足りなければ、最近登録した人で埋める。
     */
    @Transactional(readOnly = true)
    public List<UserSummary> recommendations(long meId) {
        List<User> picked = new ArrayList<>(users.findFollowedByFollowing(meId, RECOMMENDATION_COUNT));
        int rest = RECOMMENDATION_COUNT - picked.size();
        if (rest > 0) {
            List<Long> exclude = new ArrayList<>(picked.stream().map(User::getId).toList());
            exclude.add(-1L); // NOT IN () は SQL として書けないため、使われない ID を入れておく
            picked.addAll(users.findRecentExcept(meId, exclude, rest));
        }
        return summaries.of(picked, meId);
    }

    private ProfileResponse toProfile(User user, long meId) {
        return ProfileResponse.of(
                summaries.of(user, meId),
                follows.countByFollowerId(user.getId()),
                follows.countByFolloweeId(user.getId()),
                user.getCreatedAt());
    }

    /** LIKE の特別な記号（%・_）と、エスケープに使う記号（\）を、ただの文字として扱うようにする。 */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ApiException usernameTaken() {
        return new ApiException(
                ErrorCode.CONFLICT, List.of(FieldErrorDetail.of("username", FieldErrorCode.USERNAME_TAKEN)));
    }
}
