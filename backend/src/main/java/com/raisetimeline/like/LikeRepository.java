package com.raisetimeline.like;

import com.raisetimeline.post.PostCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** いいねの読み書き。 */
public interface LikeRepository extends JpaRepository<Like, Like.Key> {

    /**
     * いいねする。すでにいいね済みなら何もしない（API 設計書 A-4）。
     * 「確かめてから追加」にすると、同時に2回押されたときに主キーの重複でエラーになるため、DB に任せる。
     */
    @Modifying
    @Query(
            value = "INSERT INTO likes (post_id, user_id) VALUES (:postId, :userId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("postId") long postId, @Param("userId") long userId);

    /** いいねを取り消す。いいねしていなければ何もしない（A-4）。 */
    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.postId = :postId AND l.userId = :userId")
    int deleteByPair(@Param("postId") long postId, @Param("userId") long userId);

    long countByPostId(long postId);

    /** 投稿ごとのいいねの数を、投稿の ID の配列でまとめて数える（DB 設計書 5.5）。いいねのない投稿は行がない。 */
    @Query("SELECT new com.raisetimeline.post.PostCount(l.postId, count(l)) FROM PostLike l"
            + " WHERE l.postId IN :postIds GROUP BY l.postId")
    List<PostCount> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** postIds のうち、me がいいねしている投稿の ID（投稿カードの likedByMe をまとめて調べる）。 */
    @Query("SELECT l.postId FROM PostLike l WHERE l.userId = :me AND l.postId IN :postIds")
    List<Long> findLikedPostIdsAmong(@Param("me") long me, @Param("postIds") Collection<Long> postIds);

    /** 投稿にいいねした人（最初の読み込み）。いいねした時刻 → 利用者の ID の新しい順。 */
    @Query("SELECT l FROM PostLike l WHERE l.postId = :postId ORDER BY l.createdAt DESC, l.userId DESC")
    List<Like> findByPost(@Param("postId") long postId, Limit limit);

    /** 投稿にいいねした人（カーソルより後ろ）。 */
    @Query("SELECT l FROM PostLike l WHERE l.postId = :postId"
            + " AND (l.createdAt < :at OR (l.createdAt = :at AND l.userId < :id))"
            + " ORDER BY l.createdAt DESC, l.userId DESC")
    List<Like> findByPostAfter(
            @Param("postId") long postId, @Param("at") Instant at, @Param("id") long id, Limit limit);
}
