package com.raisetimeline.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * フォロー（DB 設計書 4.6）。「誰（follower）が誰（followee）をフォローしているか」の1行。
 * 2つの列の組を主キーにして、同じ人を2回フォローできないようにする（BR-43）。
 *
 * <p>行の追加は {@link FollowRepository#insertIfAbsent} で行う（同時に押されても壊れないように）。
 * そのため、このクラスは読むためだけに使う。
 */
@Entity
@Table(name = "follows")
@IdClass(Follow.Key.class)
public class Follow {

    @Id
    @Column(name = "follower_id")
    private long followerId;

    @Id
    @Column(name = "followee_id")
    private long followeeId;

    /** フォローした時刻。DB の既定値（now()）で入る。一覧の並びに使う（BR-44）。 */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Follow() {
        // JPA が使う
    }

    public long getFollowerId() {
        return followerId;
    }

    public long getFolloweeId() {
        return followeeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 主キー（follower_id, followee_id）。JPA が2つの列の組を主キーとして扱うためのクラス。 */
    public static class Key implements Serializable {

        private long followerId;
        private long followeeId;

        protected Key() {
            // JPA が使う
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && followerId == other.followerId && followeeId == other.followeeId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(followerId, followeeId);
        }
    }
}
