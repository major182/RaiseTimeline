package com.raisetimeline.user;

import com.raisetimeline.common.validation.CodePointLength;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * プロフィールの編集で送る値（API 設計書 4.2）。送った項目だけを変える（null の項目は変えない）。
 * 文字数はコードポイントで数える（DB 設計書 D-7）。
 */
@Schema(description = "送った項目だけを変える（null・省略した項目は変えない）。文字数はコードポイントで数える")
public record UpdateProfileRequest(
        @Schema(minLength = 1, maxLength = 50) @CodePointLength(min = 1, max = 50, message = "DISPLAY_NAME_LENGTH")
                String displayName,
        @Pattern(regexp = User.USERNAME_PATTERN, message = "USERNAME_INVALID") String username,
        @Schema(maxLength = 160) @CodePointLength(max = 160, message = "BIO_TOO_LONG") String bio) {}
