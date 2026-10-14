package com.raisetimeline.user;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.pagination.CursorPage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.raisetimeline.common.error.ApiErrors;
import com.raisetimeline.common.error.ErrorCode;

/** 利用者・プロフィールの API（API 設計書 4.2）。業務ルールは {@link UserService} に置く。 */
@RestController
@Tag(name = "利用者・プロフィール", description = "API 設計書 4.2")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** ユーザー名でプロフィールを取る（SC-05・SC-08 の URL から開くとき）。 */
    @Operation(summary = "ユーザー名でプロフィールを取る", description = "大文字・小文字を区別せずに探す")
    @ApiErrors(ErrorCode.NOT_FOUND)
    @GetMapping("/api/users/by-username/{username}")
    ProfileResponse byUsername(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String username) {
        return userService.profileByUsername(username, me.id());
    }

    /** ID でプロフィールを取る。 */
    @Operation(summary = "ID でプロフィールを取る")
    @ApiErrors(ErrorCode.NOT_FOUND)
    @GetMapping("/api/users/{userId}")
    ProfileResponse byId(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long userId) {
        return userService.profile(userId, me.id());
    }

    /** プロフィールの編集（F-US-02）。送った項目だけを変える。 */
    @Operation(
            summary = "プロフィールの編集",
            description = "送った項目だけを変える（null の項目は変えない）。自分の今のユーザー名は「使われている」にしない")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.CONFLICT})
    @PatchMapping("/api/me/profile")
    ProfileResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody UpdateProfileRequest body) {
        return userService.updateProfile(me.id(), body);
    }

    /** アイコンの変更（F-US-02）。multipart/form-data の file に画像を 1 枚。 */
    @Operation(
            summary = "アイコンの変更",
            description = "file に画像を 1 枚（5MB まで）。送らなければ 400（errors の REQUIRED）。古いファイルは消す")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.IMAGE_TOO_LARGE, ErrorCode.IMAGE_TYPE_INVALID})
    @PutMapping(path = "/api/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ProfileResponse changeAvatar(
            @AuthenticationPrincipal AuthenticatedUser me, @RequestPart(name = "file", required = false) MultipartFile file) {
        return userService.changeAvatar(me.id(), file);
    }

    /** 利用者の検索（F-US-03）。 */
    @Operation(summary = "利用者の検索", description = "q は 1〜50 文字（QUERY_LENGTH）。20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    @GetMapping("/api/users/search")
    CursorPage<UserSummary> search(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor) {
        return userService.search(q, cursor, me.id());
    }

    /** おすすめの利用者（F-US-04）。一覧の形だが、続きはない。 */
    @Operation(summary = "おすすめの利用者", description = "最大 5 人。続きはない")
    @GetMapping("/api/users/recommendations")
    Items<UserSummary> recommendations(@AuthenticationPrincipal AuthenticatedUser me) {
        return new Items<>(userService.recommendations(me.id()));
    }

    /** 続きのない一覧（API 設計書 4.2 のおすすめ：{ "items": [...] }）。 */
    record Items<T>(List<T> items) {}
}
