package com.raisetimeline.post;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 投稿の読み書き。
 *
 * <p>一覧は「投稿日時 → ID」の新しい順に並べ、カーソル（最後に返した投稿の値）より後ろを取る（DB 設計書 5.1）。
 * 同じ時刻の投稿があっても ID で順番が決まるので、重複・欠落しない。読んでいる間に新しい投稿が増えても、
 * それはカーソルより前（上）に入るので、続きの読み込みには出てこない（BR-53）。
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /** 全員の投稿（全体タブの最初の読み込み）。索引 posts_created_idx を使う。 */
    @Query("SELECT p FROM Post p ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findLatest(Limit limit);

    /** 全員の投稿（カーソルより後ろ）。 */
    @Query("SELECT p FROM Post p WHERE p.createdAt < :at OR (p.createdAt = :at AND p.id < :id)"
            + " ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findLatestBefore(@Param("at") Instant at, @Param("id") long id, Limit limit);

    /** 利用者の投稿（最初の読み込み。DB 設計書 5.4）。索引 posts_user_created_idx を使う。 */
    @Query("SELECT p FROM Post p WHERE p.userId = :userId ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findByUser(@Param("userId") long userId, Limit limit);

    /** 利用者の投稿（カーソルより後ろ）。 */
    @Query("SELECT p FROM Post p WHERE p.userId = :userId"
            + " AND (p.createdAt < :at OR (p.createdAt = :at AND p.id < :id))"
            + " ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findByUserBefore(
            @Param("userId") long userId, @Param("at") Instant at, @Param("id") long id, Limit limit);
}
