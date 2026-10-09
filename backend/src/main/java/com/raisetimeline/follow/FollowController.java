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

/** フォローの API（API 設計書 4.7）。業務ルールは {@link FollowService} に置く。 */
@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** フォローする（F-FL-01）。PUT なので、何度送っても結果は同じ。 */
    @PutMapping("/api/users/{userId}/follow")
    ResponseEntity<Void> follow(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long userId) {
        followService.follow(me.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /** フォローをやめる（F-FL-01）。 */
    @DeleteMapping("/api/users/{userId}/follow")
    ResponseEntity<Void> unfollow(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long userId) {
        followService.unfollow(me.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /** フォローの一覧（F-FL-02）。 */
    @GetMapping("/api/users/{userId}/following")
    CursorPage<UserSummary> following(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long userId,
            @RequestParam(required = false) String cursor) {
        return followService.following(userId, cursor, me.id());
    }

    /** フォロワーの一覧（F-FL-03）。 */
    @GetMapping("/api/users/{userId}/followers")
    CursorPage<UserSummary> followers(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long userId,
            @RequestParam(required = false) String cursor) {
        return followService.followers(userId, cursor, me.id());
    }
}
