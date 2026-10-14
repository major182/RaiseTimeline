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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.raisetimeline.common.error.ApiErrors;
import com.raisetimeline.common.error.ErrorCode;

/** いいねの API（API 設計書 4.6）。業務ルールは {@link LikeService} に置く。 */
@RestController
@Tag(name = "いいね", description = "API 設計書 4.6")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /** いいねする（F-LK-01）。PUT なので、何度送っても結果は同じ。 */
    @Operation(summary = "いいねする", description = "いいね済みでも何も変えずに今の状態を返す（A-4）")
    @ApiErrors(ErrorCode.NOT_FOUND)
    @PutMapping("/api/posts/{postId}/like")
    LikeResponse like(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        return likeService.like(me.id(), postId);
    }

    /** いいねを取り消す（F-LK-01）。 */
    @Operation(summary = "いいねを取り消す", description = "いいねしていなくても何も変えずに今の状態を返す（A-4）")
    @ApiErrors(ErrorCode.NOT_FOUND)
    @DeleteMapping("/api/posts/{postId}/like")
    LikeResponse unlike(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        return likeService.unlike(me.id(), postId);
    }

    /** いいねした人の一覧（F-LK-03）。 */
    @Operation(summary = "いいねした人の一覧", description = "いいねした時刻の新しい順。20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.NOT_FOUND})
    @GetMapping("/api/posts/{postId}/likes")
    CursorPage<UserSummary> likers(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @RequestParam(required = false) String cursor) {
        return likeService.likers(postId, cursor, me.id());
    }
}
