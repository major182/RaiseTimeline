package com.raisetimeline.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 利用者をログインした状態にする（セッションに保存する）。登録とログインの両方から使う。
 *
 * <p>セッションの中身は Spring Session が DB（spring_session テーブル）に保存する。
 */
@Component
public class SessionLogin {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

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
        repository.saveContext(context, request, response);
    }
}
