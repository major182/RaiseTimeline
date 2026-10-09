package com.raisetimeline.auth;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.error.FieldErrorCode;
import com.raisetimeline.common.error.FieldErrorDetail;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 認証まわりの業務ルール（要件定義書 3.1）。 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
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
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 確かめた直後に、同じ名前で別の人が登録した場合。DB の UNIQUE 索引が最後の安全網になる
            throw new ApiException(ErrorCode.CONFLICT, List.of(conflictOf(e)));
        }
    }

    @Transactional(readOnly = true)
    public User currentUser(AuthenticatedUser principal) {
        // セッションが残っていても、利用者が消えていればログインしていない扱いにする
        return users.findById(principal.id()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private static FieldErrorDetail conflictOf(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.contains("users_email_lower_key")
                ? FieldErrorDetail.of("email", FieldErrorCode.EMAIL_TAKEN)
                : FieldErrorDetail.of("username", FieldErrorCode.USERNAME_TAKEN);
    }
}
