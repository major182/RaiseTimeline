package com.raisetimeline.follow;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.user.UserSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.raisetimeline.common.error.ApiErrors;
import com.raisetimeline.common.error.ErrorCode;

/** フォローの API（API 設計書 4.7）。業務ルールは {@link FollowService} に置く。 */
@RestController
@Tag(name = "フォロー", description = "API 設計書 4.7")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** フォローする（F-FL-01）。PUT なので、何度送っても結果は同じ。 */
    @Operation(summary = "フォローする", description = "何度送っても結果は同じ（A-4）。自分自身は 400（BR-43）")
    @ApiResponse(responseCode = "204", description = "フォローした")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.NOT_FOUND})
    @PutMapping("/api/users/{userId}/follow")
    ResponseEntity<Void> follow(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long userId) {
        followService.follow(me.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /** フォローをやめる（F-FL-01）。 */
    @Operation(summary = "フォローをやめる", description = "何度送っても結果は同じ（A-4）")
    @ApiResponse(responseCode = "204", description = "やめた")
    @ApiErrors(ErrorCode.NOT_FOUND)
    @DeleteMapping("/api/users/{userId}/follow")
    ResponseEntity<Void> unfollow(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long userId) {
        followService.unfollow(me.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /** フォローの一覧（F-FL-02）。 */
    @Operation(summary = "フォローの一覧", description = "フォローした時刻の新しい順。20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.NOT_FOUND})
    @GetMapping("/api/users/{userId}/following")
    CursorPage<UserSummary> following(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long userId,
            @RequestParam(required = false) String cursor) {
        return followService.following(userId, cursor, me.id());
    }

    /** フォロワーの一覧（F-FL-03）。 */
    @Operation(summary = "フォロワーの一覧", description = "フォローされた時刻の新しい順。20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.NOT_FOUND})
    @GetMapping("/api/users/{userId}/followers")
    CursorPage<UserSummary> followers(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long userId,
            @RequestParam(required = false) String cursor) {
        return followService.followers(userId, cursor, me.id());
    }
}
