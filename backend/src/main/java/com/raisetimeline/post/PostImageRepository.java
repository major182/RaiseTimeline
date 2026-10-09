package com.raisetimeline.post;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 投稿の画像の読み書き。 */
public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    /** 投稿の画像を、投稿の ID の配列でまとめて、並び順に取る（DB 設計書 5.5）。 */
    @Query("SELECT i FROM PostImage i WHERE i.postId IN :postIds ORDER BY i.postId, i.position")
    List<PostImage> findByPostIds(@Param("postIds") Collection<Long> postIds);

    boolean existsByPostId(long postId);

    @Query("SELECT i.storageKey FROM PostImage i WHERE i.postId = :postId")
    List<String> findKeysByPostId(@Param("postId") long postId);
}
