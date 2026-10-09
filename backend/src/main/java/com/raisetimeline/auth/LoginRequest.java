package com.raisetimeline.auth;

import jakarta.validation.constraints.NotBlank;

/** ログインで送る値（API 設計書 4.1）。形式のチェックはせず、空かどうかだけを見る（BR-09）。 */
public record LoginRequest(@NotBlank(message = "REQUIRED") String email, @NotBlank(message = "REQUIRED") String password) {}
