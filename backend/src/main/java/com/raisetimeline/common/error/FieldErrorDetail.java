package com.raisetimeline.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

/** 入力欄ごとの誤り。エラーの本文の {@code errors} の1件になる。 */
public record FieldErrorDetail(
        @Schema(description = "入力欄の名前", example = "password") String field,
        @Schema(implementation = FieldErrorCode.class, description = "誤りの種類") String code,
        @Schema(description = "画面にそのまま出せる日本語") String message) {

    public static FieldErrorDetail of(String field, FieldErrorCode code) {
        return new FieldErrorDetail(field, code.name(), code.message());
    }
}
