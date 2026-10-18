package com.raisetimeline.auth;

import static com.raisetimeline.ApiExceptions.codeOf;
import static com.raisetimeline.ApiExceptions.fieldCodesOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 認証の業務ルールの単体テスト。DB（UserRepository）とパスワードのハッシュ化は偽物にする。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTests {

    private static final String EMAIL = "sato@example.com";
    private static final String PASSWORD = "pass1234";

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder);
        user = User.register("sato", EMAIL, "hash");
    }

    private void 正しいパスワード(boolean correct) {
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(correct);
    }

    // --- ログイン（BR-04・BR-08・BR-09） ---

    @Test
    @DisplayName("UT-AU-04 登録されていないメールアドレスは LOGIN_FAILED。時間をそろえるため、ダミーで照合はする")
    void 未登録のメールアドレス() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.login(new LoginRequest(EMAIL, PASSWORD)))).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(passwordEncoder).matches(any(), any());
    }

    @Test
    @DisplayName("UT-AU-04 続けて 4 回失敗するまでは LOGIN_FAILED、5 回目で LOGIN_LOCKED")
    void 五回目で止まる() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        正しいパスワード(false);
        LoginRequest wrong = new LoginRequest(EMAIL, PASSWORD);

        for (int i = 1; i <= 4; i++) {
            assertThat(codeOf(() -> service.login(wrong))).as("%d 回目", i).isEqualTo(ErrorCode.LOGIN_FAILED);
        }
        assertThat(codeOf(() -> service.login(wrong))).isEqualTo(ErrorCode.LOGIN_LOCKED);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("UT-AU-04 止めている間は、正しいパスワードでも照合せずに LOGIN_LOCKED")
    void 止めている間() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        正しいパスワード(false);
        for (int i = 0; i < AuthService.MAX_LOGIN_FAILURES; i++) {
            codeOf(() -> service.login(new LoginRequest(EMAIL, PASSWORD)));
        }

        assertThat(codeOf(() -> service.login(new LoginRequest(EMAIL, "correct1")))).isEqualTo(ErrorCode.LOGIN_LOCKED);
        verify(passwordEncoder, never()).matches("correct1", "hash");
    }

    @Test
    @DisplayName("UT-AU-04 成功すると失敗の回数が 0 に戻る（その後また 5 回まで失敗できる）")
    void 成功で回数が戻る() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong123", "hash")).thenReturn(false);
        正しいパスワード(true);
        for (int i = 0; i < 4; i++) {
            codeOf(() -> service.login(new LoginRequest(EMAIL, "wrong123")));
        }

        assertThat(service.login(new LoginRequest(EMAIL, PASSWORD))).isSameAs(user);
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(codeOf(() -> service.login(new LoginRequest(EMAIL, "wrong123")))).isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    // --- 登録（BR-02・BR-04） ---

    @Test
    @DisplayName("UT-AU-01 メールアドレスとユーザー名の両方が使われていれば、両方の欄の誤りを返す")
    void 両方使われている() {
        when(users.existsByEmail(EMAIL)).thenReturn(true);
        when(users.existsByUsername("sato")).thenReturn(true);

        SignupRequest request = new SignupRequest(EMAIL, PASSWORD, PASSWORD, "sato");
        assertThat(codeOf(() -> service.signup(request))).isEqualTo(ErrorCode.CONFLICT);
        assertThat(fieldCodesOf(() -> service.signup(request))).containsExactly("EMAIL_TAKEN", "USERNAME_TAKEN");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-AU-01 確かめた直後に別の人が登録して DB の制約に当たったら、索引の名前で欄を決める")
    void 同時の登録() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");
        when(users.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("x", new SQLException("users_email_lower_key")))
                .thenThrow(new DataIntegrityViolationException("x", new SQLException("users_username_lower_key")));

        SignupRequest request = new SignupRequest(EMAIL, PASSWORD, PASSWORD, "sato");
        assertThat(fieldCodesOf(() -> service.signup(request))).containsExactly("EMAIL_TAKEN");
        assertThat(fieldCodesOf(() -> service.signup(request))).containsExactly("USERNAME_TAKEN");
    }

    @Test
    @DisplayName("UT-AU-01 登録では、パスワードをハッシュにして保存し、表示名はユーザー名と同じにする")
    void 登録の成功() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(users.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        User saved = service.signup(new SignupRequest(EMAIL, PASSWORD, PASSWORD, "sato"));

        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getDisplayName()).isEqualTo("sato");
    }

    // --- パスワードの変更・ログイン中の利用者 ---

    @Test
    @DisplayName("UT-AU-06 今のパスワードが違えば CURRENT_PASSWORD_WRONG で、パスワードは変えない")
    void 今のパスワードが違う() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        正しいパスワード(false);

        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, "newpass1", "newpass1");
        assertThat(fieldCodesOf(() -> service.changePassword(new AuthenticatedUser(1L), request)))
                .containsExactly("CURRENT_PASSWORD_WRONG");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("UT-AU-06 今のパスワードが正しければ、新しいパスワードのハッシュに変える")
    void パスワードを変える() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        正しいパスワード(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("newhash");

        service.changePassword(new AuthenticatedUser(1L), new ChangePasswordRequest(PASSWORD, "newpass1", "newpass1"));

        assertThat(user.getPasswordHash()).isEqualTo("newhash");
    }

    @Test
    @DisplayName("UT-AU-06 トークンが有効でも、利用者が消えていればログインしていない扱い（UNAUTHENTICATED）")
    void 利用者が消えている() {
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.currentUser(new AuthenticatedUser(1L)))).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
