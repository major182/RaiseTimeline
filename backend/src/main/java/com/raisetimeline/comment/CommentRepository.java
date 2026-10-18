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
 * 一覧は「コメントした時刻 → ID」の古い順（DB 設計書 5.9）。コメントは索引 comments_post_created_idx（post_id, created_at, id）、
 * 返信は comments_parent_created_idx（parent_id, created_at, id）をそのまま使える。
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 投稿のコメント（最初の読み込み）。返信は含めない。 */
    @Query("SELECT c FROM Comment c WHERE c.postId = :postId AND c.parentId IS NULL ORDER BY c.createdAt, c.id")
    List<Comment> findByPost(@Param("postId") long postId, Limit limit);

    /** 投稿のコメント（カーソルより後ろ）。返信は含めない。 */
    @Query("SELECT c FROM Comment c WHERE c.postId = :postId AND c.parentId IS NULL"
            + " AND (c.createdAt > :at OR (c.createdAt = :at AND c.id > :id))"
            + " ORDER BY c.createdAt, c.id")
    List<Comment> findByPostAfter(
            @Param("postId") long postId, @Param("at") Instant at, @Param("id") long id, Limit limit);

    /** コメントへの返信（最初の読み込み）。 */
    @Query("SELECT c FROM Comment c WHERE c.parentId = :parentId ORDER BY c.createdAt, c.id")
    List<Comment> findReplies(@Param("parentId") long parentId, Limit limit);

    /** コメントへの返信（カーソルより後ろ）。 */
    @Query("SELECT c FROM Comment c WHERE c.parentId = :parentId"
            + " AND (c.createdAt > :at OR (c.createdAt = :at AND c.id > :id))"
            + " ORDER BY c.createdAt, c.id")
    List<Comment> findRepliesAfter(
            @Param("parentId") long parentId, @Param("at") Instant at, @Param("id") long id, Limit limit);

    /** コメントごとの返信の数を、コメントの ID の配列でまとめて数える（NF-PE-02）。返信のないコメントは行がない。 */
    @Query("SELECT new com.raisetimeline.comment.ReplyCount(c.parentId, count(c)) FROM Comment c"
            + " WHERE c.parentId IN :parentIds GROUP BY c.parentId")
    List<ReplyCount> countRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /** 投稿ごとのコメントの数（返信を含む。BR-31）を、投稿の ID の配列でまとめて数える（NF-PE-02）。コメントのない投稿は行がない。 */
    @Query("SELECT new com.raisetimeline.post.PostCount(c.postId, count(c)) FROM Comment c"
            + " WHERE c.postId IN :postIds GROUP BY c.postId")
    List<PostCount> countByPostIds(@Param("postIds") Collection<Long> postIds);
}
