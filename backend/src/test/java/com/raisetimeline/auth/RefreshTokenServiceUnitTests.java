package com.raisetimeline.auth;

import static com.raisetimeline.ApiExceptions.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.config.AuthProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * リフレッシュトークンの取り直し（ローテーション）の単体テスト（BR-07、DB 設計書 4.7）。
 * トークンの状態（有効・期限切れ・無効にした理由）ごとの分かれ道を確かめる（状態遷移・分岐網羅）。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceUnitTests {

    private static final String RAW = "raw-token";
    private static final long USER_ID = 7L;

    @Mock
    private RefreshTokenRepository tokens;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties =
                new AuthProperties("secret", "issuer", Duration.ofMinutes(15), Duration.ofDays(7), false);
        service = new RefreshTokenService(tokens, properties);
    }

    /** DB に保存されているトークンを用意する。 */
    private RefreshToken stored(Instant expiresAt) {
        RefreshToken token = new RefreshToken(USER_ID, RefreshTokenService.hash(RAW), expiresAt);
        when(tokens.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(token));
        return token;
    }

    private static Instant inOneDay() {
        return Instant.now().plus(Duration.ofDays(1));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("UT-AU-05 Cookie にトークンがなければ、DB を見ずに UNAUTHENTICATED")
    void トークンがない(String raw) {
        assertThat(codeOf(() -> service.rotate(raw))).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(tokens);
    }

    @Test
    @DisplayName("UT-AU-05 DB にないトークンは UNAUTHENTICATED")
    void 知らないトークン() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.rotate(RAW))).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("UT-AU-05 有効なトークンは使い終わりにして、同じ利用者の新しいトークンを発行する")
    void 有効なら交換する() {
        RefreshToken token = stored(inOneDay());

        RefreshTokenService.Rotation rotation = service.rotate(RAW);

        assertThat(rotation.userId()).isEqualTo(USER_ID);
        assertThat(rotation.refreshToken()).isNotEqualTo(RAW);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.wasRotated()).isTrue();
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("UT-AU-05 取り直しで使い終わったトークンがまた来たら、盗まれたとみなし利用者の全トークンを無効にする")
    void 使い回しを検知する() {
        RefreshToken token = stored(inOneDay());
        token.revoke(Instant.now(), RevokeReason.ROTATED);

        assertThat(codeOf(() -> service.rotate(RAW))).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(tokens).revokeAllByUserId(eq(USER_ID), any(), eq(RevokeReason.REUSE_DETECTED));
        verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("UT-AU-05 ログアウトで無効にしたトークンが来ても、断るだけで全トークンは無効にしない")
    void ログアウト後のトークン() {
        RefreshToken token = stored(inOneDay());
        token.revoke(Instant.now(), RevokeReason.LOGOUT);

        assertThat(codeOf(() -> service.rotate(RAW))).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(tokens, never()).revokeAllByUserId(anyLong(), any(), any());
    }

    @Test
    @DisplayName("UT-AU-05 期限切れのトークンは UNAUTHENTICATED で、新しいトークンを発行しない")
    void 期限切れ() {
        stored(Instant.now().minusSeconds(1));

        assertThat(codeOf(() -> service.rotate(RAW))).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(tokens, never()).save(any());
    }

    @Test
    @DisplayName("UT-AU-05 期限の時刻ちょうどで期限切れになる")
    void 期限の境界() {
        Instant expiresAt = Instant.parse("2026-10-18T00:00:00Z");
        RefreshToken token = new RefreshToken(USER_ID, "hash", expiresAt);

        assertThat(token.isExpired(expiresAt.minusNanos(1))).isFalse();
        assertThat(token.isExpired(expiresAt)).isTrue();
    }

    @Test
    @DisplayName("UT-AU-05 一度無効にしたトークンの理由は、あとから上書きしない")
    void 無効の理由を上書きしない() {
        RefreshToken token = new RefreshToken(USER_ID, "hash", inOneDay());
        token.revoke(Instant.now(), RevokeReason.ROTATED);
        token.revoke(Instant.now(), RevokeReason.LOGOUT);

        assertThat(token.wasRotated()).isTrue();
    }

    @Test
    @DisplayName("UT-AU-05 ログアウトでは、知らないトークンでもエラーにしない")
    void ログアウトの知らないトークン() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke(RAW);

        verify(tokens).findByTokenHash(any());
    }

    @Test
    @DisplayName("UT-AU-06 パスワードの変更では、今の端末のトークンを残して、ほかを無効にする")
    void 今の端末以外を無効にする() {
        service.revokeOthers(USER_ID, RAW);

        verify(tokens)
                .revokeOthersByUserId(
                        eq(USER_ID), eq(RefreshTokenService.hash(RAW)), any(), eq(RevokeReason.PASSWORD_CHANGED));
        verify(tokens, never()).revokeAllByUserId(anyLong(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("UT-AU-06 今の端末のトークンがなければ、全部を無効にする")
    void 全部を無効にする(String raw) {
        service.revokeOthers(USER_ID, raw);

        verify(tokens).revokeAllByUserId(eq(USER_ID), any(), eq(RevokeReason.PASSWORD_CHANGED));
    }
}
