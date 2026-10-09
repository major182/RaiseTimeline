package com.raisetimeline.auth;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・取り直し（ローテーション）・無効化（技術選定書 4.1、DB 設計書 4.7）。
 *
 * <p>トークンは推測できないランダムな 256 ビットの値。DB には SHA-256 のハッシュだけを保存する。
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    /** 無効にしたトークンの行を残す長さ。この間は、使い回しを検知できる。 */
    private static final Duration KEEP_REVOKED = Duration.ofDays(7);

    private final RefreshTokenRepository tokens;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    /** 取り直しの結果。新しいリフレッシュトークン（元の値）と、その持ち主。 */
    public record Rotation(long userId, String refreshToken) {}

    /** 新しいトークンを発行し、元の値を返す（Cookie に入れる）。 */
    @Transactional
    public String issue(long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(userId, hash(raw), Instant.now().plus(properties.refreshTokenTtl())));
        return raw;
    }

    /**
     * トークンを使って取り直す。受け取ったトークンは無効にし、新しいトークンを発行する。
     * 取り直しで使い終わったトークンがもう一度使われたら、盗まれたとみなし、その利用者のトークンをすべて無効にする。
     * ログアウト・パスワードの変更で無効にしたトークンが届くのは普通に起こるので、断るだけにする。
     * 失敗はどれも「ログインしていない」（401）として返す。例外でも無効化は取り消さない（noRollbackFor）。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String raw) {
        Instant now = Instant.now();
        RefreshToken token = find(raw);
        if (token == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (token.isRevoked()) {
            if (token.wasRotated()) {
                LOG.warn("使い終わったリフレッシュトークンが使われました。利用者 {} のトークンをすべて無効にします", token.getUserId());
                tokens.revokeAllByUserId(token.getUserId(), now, RevokeReason.REUSE_DETECTED);
            }
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (token.isExpired(now)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        token.revoke(now, RevokeReason.ROTATED);
        return new Rotation(token.getUserId(), issue(token.getUserId()));
    }

    /** トークンを無効にする（ログアウト）。ない・無効でも何もしない。 */
    @Transactional
    public void revoke(String raw) {
        RefreshToken token = find(raw);
        if (token != null) {
            token.revoke(Instant.now(), RevokeReason.LOGOUT);
        }
    }

    /** 今の端末のトークン以外を無効にする（パスワードの変更）。今の端末のトークンがなければ全部。 */
    @Transactional
    public void revokeOthers(long userId, String currentRaw) {
        Instant now = Instant.now();
        if (currentRaw == null || currentRaw.isEmpty()) {
            tokens.revokeAllByUserId(userId, now, RevokeReason.PASSWORD_CHANGED);
        } else {
            tokens.revokeOthersByUserId(userId, hash(currentRaw), now, RevokeReason.PASSWORD_CHANGED);
        }
    }

    /** 期限切れ・無効にしてから時間がたったトークンを、1 日に 1 回まとめて消す（毎日 4 時）。 */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteStaleTokens() {
        Instant now = Instant.now();
        int deleted = tokens.deleteStale(now, now.minus(KEEP_REVOKED));
        LOG.info("古いリフレッシュトークンを {} 件消しました", deleted);
    }

    private RefreshToken find(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return tokens.findByTokenHash(hash(raw)).orElse(null);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 は Java に必ずある
        }
    }
}
