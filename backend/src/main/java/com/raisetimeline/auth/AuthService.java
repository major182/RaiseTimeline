package com.raisetimeline.auth;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.error.FieldErrorCode;
import com.raisetimeline.common.error.FieldErrorDetail;
import com.raisetimeline.common.logging.LogMarkers;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 認証まわりの業務ルール（要件定義書 3.1）。 */
@Service
public class AuthService {

    /** 続けて何回失敗したらログインを止めるか（BR-08）。 */
    static final int MAX_LOGIN_FAILURES = 5;

    /** ログインを止める長さ（BR-08）。 */
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    /**
     * 登録されていないメールアドレスでも照合に同じだけ時間をかけるための、ダミーのハッシュ。
     * 応答の速さの違いから、登録済みのメールアドレスを見抜かれないようにする（BR-09）。
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 利用者を登録する（BR-01〜05）。入力の形式は、呼ぶ前に Bean Validation で確かめてある。
     * メールアドレス・ユーザー名が使われていれば 409（両方なら両方を返す）。
     */
    @Transactional
    public User signup(SignupRequest request) {
        List<FieldErrorDetail> conflicts = new ArrayList<>();
        if (users.existsByEmail(request.email())) {
            conflicts.add(FieldErrorDetail.of("email", FieldErrorCode.EMAIL_TAKEN));
        }
        if (users.existsByUsername(request.username())) {
            conflicts.add(FieldErrorDetail.of("username", FieldErrorCode.USERNAME_TAKEN));
        }
        if (!conflicts.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, conflicts);
        }
        User user = User.register(request.username(), request.email(), passwordEncoder.encode(request.password()));
        try {
            User saved = users.saveAndFlush(user);
            LOG.atInfo().addKeyValue("usr.id", saved.getId()).addKeyValue("evt.name", "signup").log("利用者を登録しました");
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 確かめた直後に、同じ名前で別の人が登録した場合。DB の UNIQUE 索引が最後の安全網になる
            throw new ApiException(ErrorCode.CONFLICT, List.of(conflictOf(e)));
        }
    }

    /**
     * ログインする（BR-04・BR-08・BR-09、DB 設計書 5.8）。
     * 失敗はエラー（例外）で知らせるが、失敗の回数は DB に残す必要があるので、例外でも取り消さない（noRollbackFor）。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public User login(LoginRequest request) {
        Instant now = Instant.now();
        User user = users.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            // メールアドレスは個人情報なので出さない（ログ設計書 6 章）
            logLoginFailure(null, "unknown_email");
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        if (user.isLoginLocked(now)) {
            // 止めている間は、パスワードを確かめず、回数も増やさない
            logLoginFailure(user.getId(), "locked");
            throw new ApiException(ErrorCode.LOGIN_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = user.recordLoginFailure(now, MAX_LOGIN_FAILURES, LOCK_DURATION);
            logLoginFailure(user.getId(), locked ? "lock_started" : "wrong_password");
            throw new ApiException(locked ? ErrorCode.LOGIN_LOCKED : ErrorCode.LOGIN_FAILED);
        }
        user.recordLoginSuccess();
        LOG.atInfo()
                .addKeyValue("usr.id", user.getId())
                .addKeyValue("evt.name", "authentication")
                .addKeyValue("evt.outcome", "success")
                .log("ログインしました");
        return user;
    }

    /** パスワードを変える（F-AU-05）。今のパスワードが違えば、その入力欄の誤りとして返す。 */
    @Transactional
    public void changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        User user = currentUser(principal);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    ErrorCode.CURRENT_PASSWORD_WRONG,
                    List.of(FieldErrorDetail.of("currentPassword", FieldErrorCode.CURRENT_PASSWORD_WRONG)));
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        LOG.atInfo().addMarker(LogMarkers.NOTICE).addKeyValue("evt.name", "password_change").log("パスワードを変更しました");
    }

    @Transactional(readOnly = true)
    public User currentUser(AuthenticatedUser principal) {
        // セッションが残っていても、利用者が消えていればログインしていない扱いにする
        return users.findById(principal.id()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    /** ログインの失敗（BR-08）。続けば総当たりの疑いがあるので、監査のため notice で残す。 */
    private static void logLoginFailure(@Nullable Long userId, String reason) {
        LOG.atInfo()
                .addMarker(LogMarkers.NOTICE)
                .addKeyValue("usr.id", userId)
                .addKeyValue("evt.name", "authentication")
                .addKeyValue("evt.outcome", "failure")
                .addKeyValue("evt.reason", reason)
                .log("ログインに失敗しました: {}", reason);
    }

    private static FieldErrorDetail conflictOf(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.contains("users_email_lower_key")
                ? FieldErrorDetail.of("email", FieldErrorCode.EMAIL_TAKEN)
                : FieldErrorDetail.of("username", FieldErrorCode.USERNAME_TAKEN);
    }
}
