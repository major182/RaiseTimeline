package com.raisetimeline.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** リフレッシュトークン（DB 設計書 4.7）。元の値ではなくハッシュだけを持つ（D-9）。 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason")
    private RevokeReason revokeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // JPA が使う
    }

    RefreshToken(long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** 取り直しで使い終わったものか（これが再び使われたら、盗まれたとみなす）。 */
    boolean wasRotated() {
        return revokeReason == RevokeReason.ROTATED;
    }

    void revoke(Instant now, RevokeReason reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }

    long getUserId() {
        return userId;
    }
}
