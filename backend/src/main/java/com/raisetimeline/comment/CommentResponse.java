package com.raisetimeline.comment;

import com.raisetimeline.user.UserSummary;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * コメント（API 設計書 3.5）。
 *
 * @param deletable コメントした本人か、投稿した本人なら true（BR-35）。true のときだけ画面は削除のメニューを出す
 */
public record CommentResponse(
        long id,
        long postId,
        UserSummary author,
        String body,
        Instant createdAt,
        @Schema(description = "コメントした本人か投稿した本人なら true（BR-35）。true のときだけ画面は削除のメニューを出す")
                boolean deletable) {}
