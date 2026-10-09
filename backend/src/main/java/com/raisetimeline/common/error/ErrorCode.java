package com.raisetimeline.common.error;

import org.springframework.http.HttpStatus;

/**
 * API が返すエラーの種類（API 設計書 2.4）。
 * 画面はこの名前（{@code code}）を見て、表示するメッセージを選ぶ。
 * メッセージは画面設計書 6 章の文言と同じにする。
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "入力内容を確認してください"),
    POST_EMPTY(HttpStatus.BAD_REQUEST, "入力してください"),
    IMAGE_TOO_MANY(HttpStatus.BAD_REQUEST, "画像は 4 枚まで添付できます"),
    CURRENT_PASSWORD_WRONG(HttpStatus.BAD_REQUEST, "現在のパスワードが違います"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "ログインの有効期限が切れました。もう一度ログインしてください"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "メールアドレスかパスワードが違います"),
    LOGIN_LOCKED(
            HttpStatus.UNAUTHORIZED, "ログインに続けて失敗したため、しばらくログインできません。15 分ほどたってからお試しください"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "この操作はできません"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "エラーが発生しました。時間をおいてもう一度お試しください"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "見つかりません"),
    CONFLICT(HttpStatus.CONFLICT, "入力内容を確認してください"),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "5MB 以下の画像を選んでください"),
    IMAGE_TYPE_INVALID(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPEG・PNG・WebP・GIF の画像を選んでください"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "エラーが発生しました。時間をおいてもう一度お試しください");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
