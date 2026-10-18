package com.raisetimeline.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.auth.ChangePasswordRequest;
import com.raisetimeline.auth.SignupRequest;
import com.raisetimeline.comment.CreateCommentRequest;
import com.raisetimeline.post.CreatePostRequest;
import com.raisetimeline.user.UpdateProfileRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 送られた値の入力チェック（Bean Validation）の単体テスト。Spring を起動せず、検証の仕組みだけを動かす。
 * 範囲の端（最小・最大）と、そのすぐ外の値を確かめる（境界値分析。テスト仕様書 2 章）。
 */
class RequestValidationTests {

    private static final String OK_EMAIL = "sato@example.com";
    private static final String OK_PASSWORD = "pass1234";
    private static final String OK_USERNAME = "raise_user";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** 誤りのあった入力欄と、誤りの種類（message に入れたコード）を「欄:コード」の形で返す。 */
    private static Set<String> errorsOf(Object request) {
        return validator.validate(request).stream()
                .map(RequestValidationTests::format)
                .collect(Collectors.toSet());
    }

    private static String format(ConstraintViolation<?> v) {
        return v.getPropertyPath() + ":" + v.getMessage();
    }

    private static SignupRequest signupWithUsername(String username) {
        return new SignupRequest(OK_EMAIL, OK_PASSWORD, OK_PASSWORD, username);
    }

    private static SignupRequest signupWithPassword(String password) {
        return new SignupRequest(OK_EMAIL, password, password, OK_USERNAME);
    }

    // --- ユーザー名（BR-02） ---

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "abcdefghijklmno", "Raise_User_01"})
    @DisplayName("UT-AU-01 ユーザー名は 4〜15 文字の半角英数字と _ なら通る")
    void ユーザー名の範囲内(String username) {
        assertThat(errorsOf(signupWithUsername(username))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcdefghijklmnop", "raise-user", "raise user", "ｒａｉｓｅ", "らいずゆーざー"})
    @DisplayName("UT-AU-01 ユーザー名が 3 文字・16 文字・使えない文字を含むと USERNAME_INVALID")
    void ユーザー名の範囲外(String username) {
        assertThat(errorsOf(signupWithUsername(username))).containsExactly("username:USERNAME_INVALID");
    }

    // --- パスワード（BR-05・BR-09） ---

    @ParameterizedTest
    @ValueSource(ints = {8, 72})
    @DisplayName("UT-AU-02 パスワードは英字と数字を含む 8〜72 文字なら通る")
    void パスワードの範囲内(int length) {
        assertThat(errorsOf(signupWithPassword("a1" + "x".repeat(length - 2)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 73})
    @DisplayName("UT-AU-02 パスワードが 7 文字・73 文字なら PASSWORD_WEAK")
    void パスワードの長さが範囲外(int length) {
        assertThat(errorsOf(signupWithPassword("a1" + "x".repeat(length - 2))))
                .containsExactly("password:PASSWORD_WEAK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh", "12345678"})
    @DisplayName("UT-AU-02 パスワードが英字だけ・数字だけなら PASSWORD_WEAK")
    void パスワードの文字の種類が足りない(String password) {
        assertThat(errorsOf(signupWithPassword(password))).containsExactly("password:PASSWORD_WEAK");
    }

    @Test
    @DisplayName("UT-AU-02 パスワード（確認）が違えば、確認の欄に PASSWORD_MISMATCH")
    void パスワードの確認が違う() {
        SignupRequest request = new SignupRequest(OK_EMAIL, OK_PASSWORD, "pass12345", OK_USERNAME);
        assertThat(errorsOf(request)).containsExactly("passwordConfirmation:PASSWORD_MISMATCH");
    }

    @Test
    @DisplayName("UT-AU-02 パスワードの変更でも、新しいパスワードに同じ条件と確認を使う")
    void パスワードの変更() {
        assertThat(errorsOf(new ChangePasswordRequest("old", "abcdefgh", "abcdefgh")))
                .containsExactly("newPassword:PASSWORD_WEAK");
        assertThat(errorsOf(new ChangePasswordRequest("old", OK_PASSWORD, "pass12345")))
                .containsExactly("newPasswordConfirmation:PASSWORD_MISMATCH");
    }

    // --- 表示名・自己紹介（BR-03・BR-25） ---

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    @DisplayName("UT-AU-03 表示名は 1〜50 文字なら通る")
    void 表示名の範囲内(int length) {
        assertThat(errorsOf(new UpdateProfileRequest("あ".repeat(length), null, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    @DisplayName("UT-AU-03 表示名が 0 文字・51 文字なら DISPLAY_NAME_LENGTH")
    void 表示名の範囲外(int length) {
        assertThat(errorsOf(new UpdateProfileRequest("あ".repeat(length), null, null)))
                .containsExactly("displayName:DISPLAY_NAME_LENGTH");
    }

    @Test
    @DisplayName("UT-US-02 自己紹介は 160 文字なら通り、161 文字なら BIO_TOO_LONG")
    void 自己紹介の境界() {
        assertThat(errorsOf(new UpdateProfileRequest(null, null, "あ".repeat(160)))).isEmpty();
        assertThat(errorsOf(new UpdateProfileRequest(null, null, "あ".repeat(161))))
                .containsExactly("bio:BIO_TOO_LONG");
    }

    // --- 投稿・コメントの本文（BR-10・BR-30） ---

    @Test
    @DisplayName("UT-PO-01 投稿の本文は 280 文字なら通り、281 文字なら BODY_TOO_LONG")
    void 投稿の本文の境界() {
        assertThat(errorsOf(new CreatePostRequest("あ".repeat(280), null))).isEmpty();
        assertThat(errorsOf(new CreatePostRequest("あ".repeat(281), null))).containsExactly("body:BODY_TOO_LONG");
    }

    @Test
    @DisplayName("UT-PO-01 絵文字は 1 文字と数えるので、絵文字 280 個の投稿は通る")
    void 絵文字の文字数() {
        // 😀 は Java の文字（char）では 2 つ分だが、コードポイントでは 1 文字（DB 設計書 D-7）
        assertThat(errorsOf(new CreatePostRequest("😀".repeat(280), null))).isEmpty();
        assertThat(errorsOf(new CreatePostRequest("😀".repeat(281), null))).containsExactly("body:BODY_TOO_LONG");
    }

    @Test
    @DisplayName("UT-CM-02 コメントは 1〜280 文字なら通る")
    void コメントの範囲内() {
        assertThat(errorsOf(new CreateCommentRequest("あ"))).isEmpty();
        assertThat(errorsOf(new CreateCommentRequest("あ".repeat(280)))).isEmpty();
    }

    @Test
    @DisplayName("UT-CM-02 コメントが空なら REQUIRED、281 文字なら BODY_TOO_LONG")
    void コメントの範囲外() {
        assertThat(errorsOf(new CreateCommentRequest(""))).containsExactly("body:REQUIRED");
        assertThat(errorsOf(new CreateCommentRequest("あ".repeat(281)))).containsExactly("body:BODY_TOO_LONG");
    }
}
