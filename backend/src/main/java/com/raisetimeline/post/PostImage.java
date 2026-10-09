package com.raisetimeline.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** 投稿の画像（DB 設計書 4.3）。ファイルは保存先（S3）に置き、ここには保存先のキーと形式・大きさだけを持つ（D-5）。 */
@Entity
@Table(name = "post_images")
public class PostImage {

    /** 1つの投稿に添付できる画像の数（BR-20）。 */
    public static final int MAX_PER_POST = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private long postId;

    /** 並び順。1〜4（送った順）。 */
    @Column(nullable = false, updatable = false)
    private short position;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(nullable = false, updatable = false)
    private int width;

    @Column(nullable = false, updatable = false)
    private int height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostImage() {
        // JPA が使う
    }

    PostImage(long postId, int position, String storageKey, String contentType, int sizeBytes, int width, int height) {
        this.postId = postId;
        this.position = (short) position;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public long getPostId() {
        return postId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
