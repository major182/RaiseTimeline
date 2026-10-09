package com.raisetimeline.like;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.user.UserSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** いいねの API（API 設計書 4.6）。業務ルールは {@link LikeService} に置く。 */
@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /** いいねする（F-LK-01）。PUT なので、何度送っても結果は同じ。 */
    @PutMapping("/api/posts/{postId}/like")
    LikeResponse like(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        return likeService.like(me.id(), postId);
    }

    /** いいねを取り消す（F-LK-01）。 */
    @DeleteMapping("/api/posts/{postId}/like")
    LikeResponse unlike(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        return likeService.unlike(me.id(), postId);
    }

    /** いいねした人の一覧（F-LK-03）。 */
    @GetMapping("/api/posts/{postId}/likes")
    CursorPage<UserSummary> likers(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @RequestParam(required = false) String cursor) {
        return likeService.likers(postId, cursor, me.id());
    }
}
