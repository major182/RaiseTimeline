package com.raisetimeline.config;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.error.ProblemDetailResponseWriter;
import com.raisetimeline.image.ImageStorage;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * 認証・認可・セキュリティのヘッダーの設定（API 設計書 2.2、技術選定書 4.1・4.7）。
 *
 * <p>JWT 方式なので、サーバーにログインの状態（セッション）を持たない。
 * アクセストークンは Authorization: Bearer ヘッダーで受け取り、署名・期限・発行者を確かめる。
 */
@Configuration
public class SecurityConfig {

    /**
     * Content-Security-Policy（技術選定書 4.7 S-03）。
     * スクリプトは自分のサイトのものだけを動かす。Material UI がスタイルを差し込むため、style は inline を許す。
     * 画像は、保存先が S3 のときだけ S3 のバケットのオリジンからの読み込みを許す（署名つき URL。NF-SE-06）。
     */
    static String contentSecurityPolicy(ImageStorage storage) {
        String imageOrigin = storage.origin();
        return String.join(
            "; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:" + (imageOrigin == null ? "" : " " + imageOrigin),
            "connect-src 'self'",
            "font-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ProblemDetailResponseWriter writer, ImageStorage storage) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        // ログインしていなくても使える API（API 設計書 2.2）
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()
                        // 開発環境の画像（/media）は、<img> が Authorization ヘッダーを送れないため、ログインではなく
                        // URL の署名と期限で守る（MediaController）
                        .requestMatchers("/api/**")
                        .authenticated()
                        // 画面のファイル（index.html など）はログインなしで返し、画面側でログイン画面へ移動する
                        .anyRequest()
                        .permitAll())
                // アクセストークン（JWT）を Authorization: Bearer ヘッダーで受け取る
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(toAuthenticatedUser()))
                        // トークンがない・期限切れ・署名が正しくない：401 を JSON で返す
                        .authenticationEntryPoint(
                                (request, response, ex) -> writer.write(request, response, ErrorCode.UNAUTHENTICATED)))
                .exceptionHandling(e -> e.authenticationEntryPoint(
                                (request, response, ex) -> writer.write(request, response, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler(
                                (request, response, ex) -> writer.write(request, response, ErrorCode.FORBIDDEN)))
                // サーバーにログインの状態を持たない（セッションを作らない）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // アクセストークンはヘッダーで送るので、CSRF の対象外。Cookie を使う API は AuthController で守る
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy(storage)))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** JWT の sub（利用者の ID）から、ログインしている利用者（AuthenticatedUser）を作る。 */
    private static Converter<Jwt, AbstractAuthenticationToken> toAuthenticatedUser() {
        return jwt -> UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(Long.parseLong(jwt.getSubject())), jwt, List.of());
    }

    /** パスワードは BCrypt でハッシュ化して保存する（BR-05）。 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
