package com.raisetimeline.auth;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
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

/** 認証の API（API 設計書 4.1）。業務ルールは {@link AuthService} などに置き、ここでは呼ぶだけにする。 */
@RestController
public class AuthController {

    /** Cookie を使う API で必須にするヘッダー。他のサイトからはこのヘッダーを付けて送れない（CSRF の対策）。 */
    static final String REQUESTED_WITH = "X-Requested-With";

    private static final String REQUESTED_WITH_VALUE = "RaiseTimeline";

    private final AuthService authService;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final RefreshTokenCookie cookie;

    public AuthController(
            AuthService authService,
            AccessTokenService accessTokens,
            RefreshTokenService refreshTokens,
            RefreshTokenCookie cookie) {
        this.authService = authService;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.cookie = cookie;
    }

    /** 利用者登録。登録したら、そのままログインした状態にする（トークンを発行する）。 */
    @PostMapping("/api/auth/signup")
    ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest body, HttpServletResponse response) {
        User user = authService.signup(body);
        return ResponseEntity.created(URI.create("/api/auth/me")).body(startSession(user, response));
    }

    /** ログイン。成功したらトークンを発行する。 */
    @PostMapping("/api/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest body, HttpServletResponse response) {
        return startSession(authService.login(body), response);
    }

    /** アクセストークンの取り直し。リフレッシュトークンも新しくする（ローテーション）。 */
    @PostMapping("/api/auth/refresh")
    AuthResponse refresh(
            @RequestHeader(value = REQUESTED_WITH, required = false) String requestedWith,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireRequestedWith(requestedWith);
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(cookie.read(request));
        cookie.write(response, rotation.refreshToken());
        User user = authService.currentUser(new AuthenticatedUser(rotation.userId()));
        return AuthResponse.of(
                accessTokens.issue(user.getId()), accessTokens.ttlSeconds(), MeResponse.from(user));
    }

    /** ログアウト。リフレッシュトークンを無効にして Cookie を消す（BR-07）。 */
    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(
            @RequestHeader(value = REQUESTED_WITH, required = false) String requestedWith,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireRequestedWith(requestedWith);
        refreshTokens.revoke(cookie.read(request));
        cookie.clear(response);
        return ResponseEntity.noContent().build();
    }

    /** パスワードの変更。他の端末のリフレッシュトークンは無効にする。 */
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
    @GetMapping("/api/auth/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return MeResponse.from(authService.currentUser(principal));
    }

    private AuthResponse startSession(User user, HttpServletResponse response) {
        cookie.write(response, refreshTokens.issue(user.getId()));
        return AuthResponse.of(accessTokens.issue(user.getId()), accessTokens.ttlSeconds(), MeResponse.from(user));
    }

    private static void requireRequestedWith(String value) {
        if (!REQUESTED_WITH_VALUE.equals(value)) {
            throw new ApiException(ErrorCode.CSRF_INVALID);
        }
    }
}
