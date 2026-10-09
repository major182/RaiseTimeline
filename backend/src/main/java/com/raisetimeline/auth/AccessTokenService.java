package com.raisetimeline.auth;

import com.raisetimeline.config.AuthProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * アクセストークン（JWT）を発行する（技術選定書 4.1）。
 * 中身は利用者の ID などだけにし、メールアドレスなどは入れない（JWT の中身は誰でも読めるため）。
 */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public AccessTokenService(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String issue(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** アクセストークンの期限（秒）。応答の expiresIn に使う。 */
    public long ttlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
