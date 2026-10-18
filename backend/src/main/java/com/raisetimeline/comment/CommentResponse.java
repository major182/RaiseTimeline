package com.raisetimeline.comment;

import com.raisetimeline.user.UserSummary;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * コメント（API 設計書 3.5）。
 *
 * @param parentId 返信先のコメントの ID。返信でなければ null（BR-37）
 * @param replyCount 返信の数。返信では 0
 * @param deletable 書いた本人か、投稿した本人なら true（BR-35）。true のときだけ画面は削除のメニューを出す
 */
public record CommentResponse(
        long id,
        long postId,
        @Schema(description = "返信先のコメントの ID。返信でなければ null（BR-37）") Long parentId,
        UserSummary author,
        String body,
        Instant createdAt,
        @Schema(description = "返信の数。返信では 0") long replyCount,
        @Schema(description = "書いた本人か投稿した本人なら true（BR-35）。true のときだけ画面は削除のメニューを出す")
                boolean deletable) {}
