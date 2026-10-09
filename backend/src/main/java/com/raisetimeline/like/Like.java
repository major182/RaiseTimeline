package com.raisetimeline.like;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * いいね（DB 設計書 4.5）。「誰（user）がどの投稿（post）にいいねしたか」の1行。
 * 2つの列の組を主キーにして、同じ投稿に2回いいねできないようにする（BR-33）。
 *
 * <p>行の追加は {@link LikeRepository#insertIfAbsent} で行う（同時に押されても壊れないように）。
 * そのため、このクラスは読むためだけに使う。
 */
@Entity(name = "PostLike") // JPQL では LIKE が予約語なので、別の名前で呼ぶ
@Table(name = "likes")
@IdClass(Like.Key.class)
public class Like {

    @Id
    @Column(name = "post_id")
    private long postId;

    @Id
    @Column(name = "user_id")
    private long userId;

    /** いいねした時刻。DB の既定値（now()）で入る。 */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Like() {
        // JPA が使う
    }

    public long getPostId() {
        return postId;
    }

    public long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 主キー（post_id, user_id）。JPA が2つの列の組を主キーとして扱うためのクラス。 */
    public static class Key implements Serializable {

        private long postId;
        private long userId;

        protected Key() {
            // JPA が使う
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && postId == other.postId && userId == other.userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, userId);
        }
    }
}
