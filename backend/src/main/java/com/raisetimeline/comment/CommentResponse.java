package com.raisetimeline.comment;

import com.raisetimeline.user.UserSummary;
import java.time.Instant;

/**
 * コメント（API 設計書 3.5）。
 *
 * @param deletable コメントした本人か、投稿した本人なら true（BR-35）。true のときだけ画面は削除のメニューを出す
 */
public record CommentResponse(
        long id, long postId, UserSummary author, String body, Instant createdAt, boolean deletable) {}
