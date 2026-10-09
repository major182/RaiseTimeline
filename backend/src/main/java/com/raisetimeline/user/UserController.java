package com.raisetimeline.user;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.pagination.CursorPage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 利用者・プロフィールの API（API 設計書 4.2）。業務ルールは {@link UserService} に置く。 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** ユーザー名でプロフィールを取る（SC-05・SC-08 の URL から開くとき）。 */
    @GetMapping("/api/users/by-username/{username}")
    ProfileResponse byUsername(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String username) {
        return userService.profileByUsername(username, me.id());
    }

    /** ID でプロフィールを取る。 */
    @GetMapping("/api/users/{userId}")
    ProfileResponse byId(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long userId) {
        return userService.profile(userId, me.id());
    }

    /** プロフィールの編集（F-US-02）。送った項目だけを変える。 */
    @PatchMapping("/api/me/profile")
    ProfileResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody UpdateProfileRequest body) {
        return userService.updateProfile(me.id(), body);
    }

    /** 利用者の検索（F-US-03）。 */
    @GetMapping("/api/users/search")
    CursorPage<UserSummary> search(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor) {
        return userService.search(q, cursor, me.id());
    }

    /** おすすめの利用者（F-US-04）。一覧の形だが、続きはない。 */
    @GetMapping("/api/users/recommendations")
    Items<UserSummary> recommendations(@AuthenticationPrincipal AuthenticatedUser me) {
        return new Items<>(userService.recommendations(me.id()));
    }

    /** 続きのない一覧（API 設計書 4.2 のおすすめ：{ "items": [...] }）。 */
    record Items<T>(List<T> items) {}
}
