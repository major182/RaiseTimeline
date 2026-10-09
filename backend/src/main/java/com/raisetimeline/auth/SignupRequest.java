package com.raisetimeline.auth;

import com.raisetimeline.common.validation.FieldsMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 利用者登録で送る値（API 設計書 4.1）。
 * 注釈の message には、誤りの種類（FieldErrorCode の名前）を書く。
 */
@FieldsMatch(field = "password", confirmation = "passwordConfirmation")
public record SignupRequest(
        @NotBlank(message = "REQUIRED") @Email(message = "EMAIL_INVALID") @Size(max = 254, message = "EMAIL_INVALID")
                String email,
        @NotBlank(message = "REQUIRED") @Pattern(regexp = PasswordRule.PATTERN, message = "PASSWORD_WEAK")
                String password,
        @NotBlank(message = "REQUIRED") String passwordConfirmation,
        @NotBlank(message = "REQUIRED") @Pattern(regexp = "^[A-Za-z0-9_]{4,15}$", message = "USERNAME_INVALID")
                String username) {}
