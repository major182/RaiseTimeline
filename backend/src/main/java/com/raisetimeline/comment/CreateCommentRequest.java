package com.raisetimeline.comment;

import com.raisetimeline.common.validation.CodePointLength;
import jakarta.validation.constraints.NotBlank;

/** コメントで送る値（API 設計書 4.5）。1〜280 文字（BR-30）。空白だけは空とみなす。 */
public record CreateCommentRequest(
        @NotBlank(message = "REQUIRED") @CodePointLength(max = Comment.BODY_MAX_LENGTH, message = "BODY_TOO_LONG")
                String body) {}
