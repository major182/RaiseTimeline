package com.raisetimeline.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/** 利用者（DB 設計書 4.1）。利用者は変わらない番号（id）で見分ける（D-1）。 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String bio = "";

    @Column(name = "avatar_key")
    private String avatarKey;

    @Column(name = "last_timeline_viewed_at")
    private Instant lastTimelineViewedAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // JPA が使う
    }

    /** 登録する利用者を作る。表示名は最初はユーザー名と同じにする（BR-01）。 */
    public static User register(String username, String email, String passwordHash) {
        User user = new User();
        user.username = username;
        user.displayName = username;
        user.email = email;
        user.passwordHash = passwordHash;
        return user;
    }

    /** この時刻にログインが止められているか（BR-08）。 */
    public boolean isLoginLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * ログインの失敗を記録する（BR-08、DB 設計書 5.8）。
     * 続けて maxFailures 回失敗したら lockDuration の間ログインを止め、回数を 0 に戻す。
     *
     * @return 今回の失敗でログインを止めたら true
     */
    public boolean recordLoginFailure(Instant now, int maxFailures, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount < maxFailures) {
            return false;
        }
        failedLoginCount = 0;
        lockedUntil = now.plus(lockDuration);
        return true;
    }

    /** ログインに成功したら、失敗の回数と停止を消す。 */
    public void recordLoginSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
