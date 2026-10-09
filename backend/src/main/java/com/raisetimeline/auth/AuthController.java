package com.raisetimeline.auth;

import com.raisetimeline.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 認証の API（API 設計書 4.1）。業務ルールは {@link AuthService} に置き、ここでは呼ぶだけにする。 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final SessionLogin sessionLogin;

    public AuthController(AuthService authService, SessionLogin sessionLogin) {
        this.authService = authService;
        this.sessionLogin = sessionLogin;
    }

    /** 利用者登録。登録したら、そのままログインした状態にする。 */
    @PostMapping("/api/auth/signup")
    ResponseEntity<MeResponse> signup(
            @Valid @RequestBody SignupRequest body, HttpServletRequest request, HttpServletResponse response) {
        User user = authService.signup(body);
        sessionLogin.login(user.getId(), request, response);
        return ResponseEntity.created(URI.create("/api/auth/me")).body(MeResponse.from(user));
    }

    /** ログイン。成功したらセッションを作り、ログインしている利用者を返す。 */
    @PostMapping("/api/auth/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        User user = authService.login(body);
        sessionLogin.login(user.getId(), request, response);
        return MeResponse.from(user);
    }

    /** ログアウト。セッションを消し、すぐにログインしていない状態にする（BR-07）。 */
    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionLogin.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    /** ログインしている利用者。画面は起動したときに呼び、401 ならログイン画面へ移動する。 */
    @GetMapping("/api/auth/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return MeResponse.from(authService.currentUser(principal));
    }
}
