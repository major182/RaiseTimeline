package com.raisetimeline.common.logging;

import com.raisetimeline.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ログインしている利用者の ID を MDC（{@code usr.id}）に入れる（ログ設計書 4 章）。
 *
 * <p>アクセストークンを確かめた後でないと利用者が分からないため、Spring Security のフィルタの中
 * （SecurityConfig で BearerTokenAuthenticationFilter の後）に置く。MDC から消すのは {@link RequestLoggingFilter}。
 * Bean にするとフィルタが二重に登録されるので、SecurityConfig で new して使う。
 */
public class UserIdLoggingFilter extends OncePerRequestFilter {

    /** MDC の名前（Datadog の標準属性）。 */
    public static final String USER_ID = "usr.id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            MDC.put(USER_ID, String.valueOf(user.id()));
        }
        chain.doFilter(request, response);
    }
}
