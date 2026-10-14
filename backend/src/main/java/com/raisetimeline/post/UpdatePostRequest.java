package com.raisetimeline.post;

import com.raisetimeline.common.validation.CodePointLength;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** 投稿の編集で送る値（API 設計書 4.4）。変えられるのは本文だけ（BR-15）。 */
public record UpdatePostRequest(
        @Schema(maxLength = Post.BODY_MAX_LENGTH)
                @NotNull(message = "REQUIRED") @CodePointLength(max = Post.BODY_MAX_LENGTH, message = "BODY_TOO_LONG")
                String body) {}
