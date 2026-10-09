package com.raisetimeline.auth;

import com.raisetimeline.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークンの Cookie（API 設計書 2.2）。
 * JavaScript から読めず（HttpOnly）、HTTPS のときだけ（Secure）、同じサイトからだけ（SameSite=Strict）、
 * 認証の API（/api/auth）にだけ送られるようにする。
 */
@Component
public class RefreshTokenCookie {

    static final String NAME = "REFRESH_TOKEN";
    private static final String PATH = "/api/auth";

    private final AuthProperties properties;

    public RefreshTokenCookie(AuthProperties properties) {
        this.properties = properties;
    }

    public void write(HttpServletResponse response, String token) {
        add(response, token, properties.refreshTokenTtl());
    }

    /** Cookie を消す指示を返す（ログアウト）。 */
    public void clear(HttpServletResponse response) {
        add(response, "", Duration.ZERO);
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void add(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
