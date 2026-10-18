package com.raisetimeline.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 利用者のエンティティの単体テスト。ログインの一時停止（BR-08）の時刻の境界を、時刻を決めて確かめる。 */
class UserUnitTests {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    @Test
    @DisplayName("UT-AU-04 5 回目の失敗で止め、止めた時刻から 15 分ちょうどで戻る")
    void 十五分で戻る() {
        User user = User.register("sato", "sato@example.com", "hash");
        for (int i = 1; i <= 4; i++) {
            assertThat(user.recordLoginFailure(NOW, 5, LOCK)).as("%d 回目", i).isFalse();
        }
        assertThat(user.recordLoginFailure(NOW, 5, LOCK)).isTrue();

        assertThat(user.isLoginLocked(NOW.plus(LOCK).minusNanos(1))).isTrue();
        assertThat(user.isLoginLocked(NOW.plus(LOCK))).isFalse();
    }

    @Test
    @DisplayName("UT-AU-04 止めたら回数を 0 に戻す（戻ったあとは、また 5 回まで失敗できる）")
    void 止めたら回数を戻す() {
        User user = User.register("sato", "sato@example.com", "hash");
        for (int i = 0; i < 5; i++) {
            user.recordLoginFailure(NOW, 5, LOCK);
        }
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("UT-US-01 プロフィールの編集では、null の項目（送られなかった項目）を変えない")
    void 送った項目だけ変える() {
        User user = User.register("sato", "sato@example.com", "hash");
        user.updateProfile(null, "sato_new", null);

        assertThat(user.getUsername()).isEqualTo("sato_new");
        assertThat(user.getDisplayName()).isEqualTo("sato");
        assertThat(user.getBio()).isEmpty();
    }
}
