package com.raisetimeline.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * アクセストークン（JWT）の作成と検証の設定（技術選定書 4.1）。
 * 署名は HS256（共通の鍵）。検証では、署名・期限・発行者（iss）を確かめる。
 */
@Configuration
public class JwtConfig {

    /** HS256 の鍵は 256 ビット（32 バイト）以上が必要。 */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSecretKey(AuthProperties properties) {
        if (properties.jwtSecret() == null || properties.jwtSecret().isBlank()) {
            throw new IllegalStateException("app.auth.jwt-secret（環境変数 JWT_SECRET）が設定されていません");
        }
        byte[] bytes = Base64.getDecoder().decode(properties.jwtSecret());
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.auth.jwt-secret は 256 ビット（32 バイト）以上にしてください");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 期限（exp）と発行者（iss）を確かめる
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
