package com.raisetimeline.auth;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.image.ImageStorage;
import com.raisetimeline.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.raisetimeline.common.error.ApiErrors;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

/** 認証の API（API 設計書 4.1）。業務ルールは {@link AuthService} などに置き、ここでは呼ぶだけにする。 */
@RestController
@Tag(name = "認証", description = "登録・ログイン・トークンの取り直し・ログアウト・パスワードの変更（API 設計書 2.2・4.1）")
public class AuthController {

    /** Cookie を使う API で必須にするヘッダー。他のサイトからはこのヘッダーを付けて送れない（CSRF の対策）。 */
    static final String REQUESTED_WITH = "X-Requested-With";

    private static final String REQUESTED_WITH_VALUE = "RaiseTimeline";

    private static final String CSRF_HEADER = "CSRF の対策。値は RaiseTimeline。ないときは 403（CSRF_INVALID）";

    private static final String REFRESH_COOKIE =
            "REFRESH_TOKEN（HttpOnly・Secure・SameSite=Strict・Path=/api/auth、期限 7 日。開発環境だけ Secure なし）";

    private final AuthService authService;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final RefreshTokenCookie cookie;
    private final ImageStorage storage;

    public AuthController(
            AuthService authService,
            AccessTokenService accessTokens,
            RefreshTokenService refreshTokens,
            RefreshTokenCookie cookie,
            ImageStorage storage) {
        this.authService = authService;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.cookie = cookie;
        this.storage = storage;
    }

    /** 利用者登録。登録したら、そのままログインした状態にする（トークンを発行する）。 */
    @Operation(summary = "利用者登録", description = "登録したら、そのままログインした状態になる。表示名はユーザー名と同じ値になる（BR-01）")
    @SecurityRequirements
    @ApiResponse(
            responseCode = "201",
            description = "登録した",
            useReturnTypeSchema = true,
            headers = @Header(name = "Set-Cookie", description = REFRESH_COOKIE, schema = @Schema(type = "string")))
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.CONFLICT})
    @PostMapping("/api/auth/signup")
    ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest body, HttpServletResponse response) {
        User user = authService.signup(body);
        return ResponseEntity.created(URI.create("/api/auth/me")).body(startSession(user, response));
    }

    /** ログイン。成功したらトークンを発行する。 */
    @Operation(summary = "ログイン", description = "未登録のメールアドレスも LOGIN_FAILED にする（BR-09）。続けて失敗すると一時停止する（BR-08）")
    @SecurityRequirements
    @ApiResponse(
            responseCode = "200",
            description = "ログインした",
            useReturnTypeSchema = true,
            headers = @Header(name = "Set-Cookie", description = REFRESH_COOKIE, schema = @Schema(type = "string")))
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.LOGIN_FAILED, ErrorCode.LOGIN_LOCKED})
    @PostMapping("/api/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest body, HttpServletResponse response) {
        return startSession(authService.login(body), response);
    }

    /** アクセストークンの取り直し。リフレッシュトークンも新しくする（ローテーション）。 */
    @Operation(
            summary = "アクセストークンの取り直し",
            description = "リフレッシュトークンも新しくなる。無効にしたトークンが再び送られたら、その利用者のトークンをすべて無効にして 401")
    @SecurityRequirements
    @Parameter(
            in = ParameterIn.COOKIE,
            name = RefreshTokenCookie.NAME,
            required = true,
            description = "リフレッシュトークン",
            schema = @Schema(type = "string"))
    @ApiResponse(
            responseCode = "200",
            description = "取り直した",
            useReturnTypeSchema = true,
            headers = @Header(name = "Set-Cookie", description = REFRESH_COOKIE, schema = @Schema(type = "string")))
    @ApiErrors({ErrorCode.UNAUTHENTICATED, ErrorCode.CSRF_INVALID})
    @PostMapping("/api/auth/refresh")
    AuthResponse refresh(
            @Parameter(required = true, description = CSRF_HEADER, example = REQUESTED_WITH_VALUE)
                    @RequestHeader(value = REQUESTED_WITH, required = false)
                    String requestedWith,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireRequestedWith(requestedWith);
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(cookie.read(request));
        cookie.write(response, rotation.refreshToken());
        User user = authService.currentUser(new AuthenticatedUser(rotation.userId()));
        return AuthResponse.of(
                accessTokens.issue(user.getId()), accessTokens.ttlSeconds(), MeResponse.from(user, storage));
    }

    /** ログアウト。リフレッシュトークンを無効にして Cookie を消す（BR-07）。 */
    @Operation(summary = "ログアウト", description = "リフレッシュトークンを無効にして Cookie を消す。トークンがない・無効でも 204")
    @SecurityRequirements
    @Parameter(
            in = ParameterIn.COOKIE,
            name = RefreshTokenCookie.NAME,
            description = "リフレッシュトークン",
            schema = @Schema(type = "string"))
    @ApiResponse(responseCode = "204", description = "ログアウトした（REFRESH_TOKEN の Cookie を消す）")
    @ApiErrors(ErrorCode.CSRF_INVALID)
    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(
            @Parameter(required = true, description = CSRF_HEADER, example = REQUESTED_WITH_VALUE)
                    @RequestHeader(value = REQUESTED_WITH, required = false)
                    String requestedWith,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireRequestedWith(requestedWith);
        refreshTokens.revoke(cookie.read(request));
        cookie.clear(response);
        return ResponseEntity.noContent().build();
    }

    /** パスワードの変更。他の端末のリフレッシュトークンは無効にする。 */
    @Operation(summary = "パスワードの変更", description = "今の端末以外のリフレッシュトークンをすべて無効にする")
    @ApiResponse(responseCode = "204", description = "変更した")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.CURRENT_PASSWORD_WRONG})
    @PutMapping("/api/me/password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request) {
        authService.changePassword(principal, body);
        refreshTokens.revokeOthers(principal.id(), cookie.read(request));
        return ResponseEntity.noContent().build();
    }

    /** ログインしている利用者。 */
    @Operation(summary = "ログインしている利用者", description = "メールアドレスを返すのはこの API だけ")
    @GetMapping("/api/auth/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return MeResponse.from(authService.currentUser(principal), storage);
    }

    private AuthResponse startSession(User user, HttpServletResponse response) {
        cookie.write(response, refreshTokens.issue(user.getId()));
        return AuthResponse.of(accessTokens.issue(user.getId()), accessTokens.ttlSeconds(), MeResponse.from(user, storage));
    }

    private static void requireRequestedWith(String value) {
        if (!REQUESTED_WITH_VALUE.equals(value)) {
            throw new ApiException(ErrorCode.CSRF_INVALID);
        }
    }
}
