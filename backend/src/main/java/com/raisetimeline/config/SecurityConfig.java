package com.raisetimeline.config;

import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.error.ProblemDetailResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

/**
 * 認証・CSRF の設定（API 設計書 2.2、5 章）。
 *
 * <p>ログインは React から JSON で送る自作の API（{@code POST /api/auth/login}）で行うため、
 * Spring Security の標準のログイン画面・ログアウト・Basic 認証は使わない。
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailResponseWriter writer) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        // ログインしていなくても使える API（API 設計書 2.2）
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/**", "/media/**").authenticated()
                        // 画面のファイル（index.html など）はログインなしで返し、画面側でログイン画面へ移動する
                        .anyRequest().permitAll())
                // JavaScript で動く画面向けの標準の CSRF 対策。XSRF-TOKEN Cookie の値を X-XSRF-TOKEN ヘッダーで送らせる
                .csrf(csrf -> csrf.spa())
                .exceptionHandling(e -> e
                        // ログインしていない：401。画面へのリダイレクトはせず JSON を返す
                        .authenticationEntryPoint(
                                (request, response, ex) -> writer.write(request, response, ErrorCode.UNAUTHENTICATED))
                        // CSRF トークンの不一致と、権限がないときを分けて返す
                        .accessDeniedHandler((request, response, ex) -> writer.write(
                                request,
                                response,
                                ex instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** パスワードは BCrypt でハッシュ化して保存する（BR-05）。 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
