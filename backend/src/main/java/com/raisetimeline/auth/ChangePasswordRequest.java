package com.raisetimeline.auth;

import com.raisetimeline.common.validation.FieldsMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** パスワードの変更で送る値（API 設計書 4.1）。新しいパスワードの条件は登録と同じ（BR-05）。 */
@FieldsMatch(field = "newPassword", confirmation = "newPasswordConfirmation")
public record ChangePasswordRequest(
        @NotBlank(message = "REQUIRED") String currentPassword,
        @NotBlank(message = "REQUIRED") @Pattern(regexp = PasswordRule.PATTERN, message = "PASSWORD_WEAK")
                String newPassword,
        @NotBlank(message = "REQUIRED") String newPasswordConfirmation) {}
