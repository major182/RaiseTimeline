package com.raisetimeline.comment;

import com.raisetimeline.post.PostCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * コメントの読み書き。
 * 一覧は「コメントした時刻 → ID」の古い順。索引 comments_post_created_idx（post_id, created_at, id）をそのまま使える。
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 投稿のコメント（最初の読み込み）。 */
    @Query("SELECT c FROM Comment c WHERE c.postId = :postId ORDER BY c.createdAt, c.id")
    List<Comment> findByPost(@Param("postId") long postId, Limit limit);

    /** 投稿のコメント（カーソルより後ろ）。 */
    @Query("SELECT c FROM Comment c WHERE c.postId = :postId"
            + " AND (c.createdAt > :at OR (c.createdAt = :at AND c.id > :id))"
            + " ORDER BY c.createdAt, c.id")
    List<Comment> findByPostAfter(
            @Param("postId") long postId, @Param("at") Instant at, @Param("id") long id, Limit limit);

    /** 投稿ごとのコメントの数を、投稿の ID の配列でまとめて数える（NF-PE-02）。コメントのない投稿は行がない。 */
    @Query("SELECT new com.raisetimeline.post.PostCount(c.postId, count(c)) FROM Comment c"
            + " WHERE c.postId IN :postIds GROUP BY c.postId")
    List<PostCount> countByPostIds(@Param("postIds") Collection<Long> postIds);
}
