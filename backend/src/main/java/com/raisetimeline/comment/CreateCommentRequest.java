package com.raisetimeline.comment;

import com.raisetimeline.common.validation.CodePointLength;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * コメント・返信で送る値（API 設計書 4.5）。1〜280 文字（BR-30）。空白だけは空とみなす。
 *
 * @param parentId 返信先のコメントの ID。コメントなら送らない（null）
 */
public record CreateCommentRequest(
        @Schema(maxLength = Comment.BODY_MAX_LENGTH)
                @NotBlank(message = "REQUIRED") @CodePointLength(max = Comment.BODY_MAX_LENGTH, message = "BODY_TOO_LONG")
                String body,
        @Schema(description = "返信先のコメントの ID。返信先が返信なら、その返信先への返信になる（BR-37）。コメントなら送らない")
                Long parentId) {

    /** コメント（返信ではない）で送る値。 */
    public CreateCommentRequest(String body) {
        this(body, null);
    }
}
