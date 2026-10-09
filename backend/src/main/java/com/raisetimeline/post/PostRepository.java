package com.raisetimeline.post;

import org.springframework.data.jpa.repository.JpaRepository;

/** 投稿の読み書き。 */
public interface PostRepository extends JpaRepository<Post, Long> {}
