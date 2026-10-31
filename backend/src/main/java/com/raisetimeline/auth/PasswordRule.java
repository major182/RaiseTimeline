package com.raisetimeline.auth;

/** パスワードの条件（BR-05）。登録とパスワードの変更で同じものを使う。 */
final class PasswordRule {

    /** 半角の英数字・記号で 8〜72 文字、英字と数字をそれぞれ 1 文字以上。72 は BCrypt が扱える長さの上限。 */
    static final String PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[\x20-\x7E]{8,72}$";

    private PasswordRule() {}
}
