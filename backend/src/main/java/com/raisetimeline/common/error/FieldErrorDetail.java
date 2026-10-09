package com.raisetimeline.common.error;

/** 入力欄ごとの誤り。エラーの本文の {@code errors} の1件になる。 */
public record FieldErrorDetail(String field, String code, String message) {

    public static FieldErrorDetail of(String field, FieldErrorCode code) {
        return new FieldErrorDetail(field, code.name(), code.message());
    }
}
