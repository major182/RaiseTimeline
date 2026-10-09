package com.raisetimeline.auth;

/** パスワードの条件（BR-05）。登録とパスワードの変更で同じものを使う。 */
final class PasswordRule {

    /** 8〜72 文字で、英字と数字をそれぞれ 1 文字以上。72 は BCrypt が扱える長さの上限。 */
    static final String PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$";

    private PasswordRule() {}
}
