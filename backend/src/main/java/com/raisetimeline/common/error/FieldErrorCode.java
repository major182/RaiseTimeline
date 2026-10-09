package com.raisetimeline.common.error;

/**
 * 入力欄ごとの誤りの種類（API 設計書 2.4 の {@code errors[].code}）。
 *
 * <p>Bean Validation の注釈では {@code message} にこの名前を書く（例：{@code @NotBlank(message = "REQUIRED")}）。
 * 例外処理でこの名前から日本語のメッセージに置き換える。
 */
public enum FieldErrorCode {
    REQUIRED("入力してください"),
    EMAIL_INVALID("メールアドレスの形式が正しくありません"),
    EMAIL_TAKEN("このメールアドレスは既に使われています"),
    PASSWORD_WEAK("8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください"),
    PASSWORD_MISMATCH("パスワードが一致しません"),
    USERNAME_INVALID("4〜15 文字の半角英数字と「_」で入力してください"),
    USERNAME_TAKEN("このユーザー名は既に使われています"),
    DISPLAY_NAME_LENGTH("1〜50 文字で入力してください"),
    BIO_TOO_LONG("160 文字以内で入力してください"),
    BODY_TOO_LONG("280 文字以内で入力してください"),
    QUERY_LENGTH("1〜50 文字で入力してください");

    private final String message;

    FieldErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
