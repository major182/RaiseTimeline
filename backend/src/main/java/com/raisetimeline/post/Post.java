package com.raisetimeline.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** 投稿（DB 設計書 4.2）。 */
@Entity
@Table(name = "posts")
public class Post {

    /** 本文の文字数の上限（BR-10）。コードポイントで数える（D-7）。 */
    public static final int BODY_MAX_LENGTH = 280;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(nullable = false)
    private String body;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Post() {
        // JPA が使う
    }

    Post(long userId, String body) {
        this.userId = userId;
        this.body = body;
    }

    /** 本文を変える（BR-15）。「編集済み」の印（editedAt）を今にする（BR-16）。 */
    void edit(String body, Instant now) {
        this.body = body;
        this.editedAt = now;
    }

    /** この利用者の投稿か（BR-12。編集・削除できるのは本人だけ）。 */
    public boolean isOwnedBy(long userId) {
        return this.userId == userId;
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

    public long getUserId() {
        return userId;
    }

    public String getBody() {
        return body;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
