package com.raisetimeline.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/** コメント（DB 設計書 4.4）。 */
@Entity
@Table(name = "comments")
public class Comment {

    /** 本文の文字数の上限（BR-30）。コードポイントで数える（D-7）。 */
    static final int BODY_MAX_LENGTH = 280;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private long postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Comment() {
        // JPA が使う
    }

    Comment(long postId, long userId, String body) {
        this.postId = postId;
        this.userId = userId;
        this.body = body;
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

    public long getPostId() {
        return postId;
    }

    public long getUserId() {
        return userId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
