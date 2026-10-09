package com.raisetimeline.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 認証の設定（application.yaml の app.auth。技術選定書 4.1）。
 *
 * @param jwtSecret アクセストークンの署名の鍵（Base64。256 ビット以上）
 * @param issuer アクセストークンの発行者（iss）
 * @param accessTokenTtl アクセストークンの期限
 * @param refreshTokenTtl リフレッシュトークンの期限
 * @param cookieSecure リフレッシュトークンの Cookie に Secure を付けるか
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, boolean cookieSecure) {}
