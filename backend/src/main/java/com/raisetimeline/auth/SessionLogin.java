package com.raisetimeline.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * ログインした状態にする・やめる（セッションの保存と破棄）。登録・ログイン・ログアウトから使う。
 *
 * <p>セッションの中身は Spring Session が DB（spring_session テーブル）に保存する。
 * ログイン・ログアウトのたびに CSRF トークンも作り直す（API 設計書 2.2）。
 */
@Component
public class SessionLogin {

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final CsrfTokenRepository csrfTokenRepository;

    public SessionLogin(CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public void login(long userId, HttpServletRequest request, HttpServletResponse response) {
        // セッション ID を作り直す。ログイン前の ID を他人に知られていても、乗っ取られないようにする（セッション固定攻撃の対策）
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(userId), null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        // ログイン前に配った CSRF トークンを使えなくし、新しいトークンの Cookie を返す
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            // Spring Session が DB のセッションの行を消し、SESSION Cookie を消す指示を返す
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        // CSRF トークンの Cookie も消す。画面は次の操作の前に GET /api/auth/csrf を呼び直す
        csrfTokenRepository.saveToken(null, request, response);
    }
}
