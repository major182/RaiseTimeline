package com.raisetimeline.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** リフレッシュトークンの読み書き。 */
interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 利用者の有効なトークンをすべて無効にする（使い回しの検知）。 */
    @Modifying
    @Query(
            "UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason"
                    + " WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllByUserId(
            @Param("userId") long userId, @Param("now") Instant now, @Param("reason") RevokeReason reason);

    /** 指定したトークン以外の、利用者の有効なトークンをすべて無効にする（パスワードの変更）。 */
    @Modifying
    @Query(
            "UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason"
                    + " WHERE t.userId = :userId AND t.revokedAt IS NULL AND t.tokenHash <> :keepHash")
    int revokeOthersByUserId(
            @Param("userId") long userId,
            @Param("keepHash") String keepHash,
            @Param("now") Instant now,
            @Param("reason") RevokeReason reason);

    /** 期限が切れたもの・無効にしてから時間がたったものを消す（DB 設計書 4.7）。 */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now OR t.revokedAt < :revokedBefore")
    int deleteStale(@Param("now") Instant now, @Param("revokedBefore") Instant revokedBefore);
}
