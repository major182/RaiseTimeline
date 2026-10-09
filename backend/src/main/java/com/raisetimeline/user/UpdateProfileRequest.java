package com.raisetimeline.user;

import com.raisetimeline.common.validation.CodePointLength;
import jakarta.validation.constraints.Pattern;

/**
 * プロフィールの編集で送る値（API 設計書 4.2）。送った項目だけを変える（null の項目は変えない）。
 * 文字数はコードポイントで数える（DB 設計書 D-7）。
 */
public record UpdateProfileRequest(
        @CodePointLength(min = 1, max = 50, message = "DISPLAY_NAME_LENGTH") String displayName,
        @Pattern(regexp = User.USERNAME_PATTERN, message = "USERNAME_INVALID") String username,
        @CodePointLength(max = 160, message = "BIO_TOO_LONG") String bio) {}
