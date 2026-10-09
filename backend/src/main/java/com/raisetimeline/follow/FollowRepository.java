package com.raisetimeline.follow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * フォローの読み書き。
 *
 * <p>一覧は「フォローした時刻 → 利用者の ID」の新しい順に並べ、カーソル（最後に返した行の値）より後ろを取る。
 * 時刻が同じ行があっても、ID で順番が決まるので、重複・欠落しない（DB 設計書 5.1 と同じ考え方）。
 * 最初の読み込みと続きの読み込みで、SQL を分けている（カーソルがないときの null の扱いを SQL に持ち込まないため）。
 */
public interface FollowRepository extends JpaRepository<Follow, Follow.Key> {

    /**
     * フォローする。すでにフォローしていれば何もしない（API 設計書 A-4）。
     * 「確かめてから追加」にすると、同時に2回押されたときに主キーの重複でエラーになるため、DB に任せる。
     */
    @Modifying
    @Query(
            value = "INSERT INTO follows (follower_id, followee_id) VALUES (:followerId, :followeeId)"
                    + " ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    /** フォローをやめる。フォローしていなければ何もしない（A-4）。 */
    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :followerId AND f.followeeId = :followeeId")
    int deleteByPair(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

    /** ids のうち、me がフォローしている人の ID（利用者の要約の followedByMe をまとめて調べる）。 */
    @Query("SELECT f.followeeId FROM Follow f WHERE f.followerId = :me AND f.followeeId IN :ids")
    List<Long> findFolloweeIdsAmong(@Param("me") long me, @Param("ids") Collection<Long> ids);

    long countByFollowerId(long followerId);

    long countByFolloweeId(long followeeId);

    /** userId がフォローしている人（最初の読み込み）。 */
    @Query("SELECT f FROM Follow f WHERE f.followerId = :userId ORDER BY f.createdAt DESC, f.followeeId DESC")
    List<Follow> findFollowing(@Param("userId") long userId, Limit limit);

    /** userId がフォローしている人（カーソルより後ろ）。 */
    @Query("SELECT f FROM Follow f WHERE f.followerId = :userId"
            + " AND (f.createdAt < :at OR (f.createdAt = :at AND f.followeeId < :id))"
            + " ORDER BY f.createdAt DESC, f.followeeId DESC")
    List<Follow> findFollowingAfter(
            @Param("userId") long userId, @Param("at") Instant at, @Param("id") long id, Limit limit);

    /** userId をフォローしている人（最初の読み込み）。 */
    @Query("SELECT f FROM Follow f WHERE f.followeeId = :userId ORDER BY f.createdAt DESC, f.followerId DESC")
    List<Follow> findFollowers(@Param("userId") long userId, Limit limit);

    /** userId をフォローしている人（カーソルより後ろ）。 */
    @Query("SELECT f FROM Follow f WHERE f.followeeId = :userId"
            + " AND (f.createdAt < :at OR (f.createdAt = :at AND f.followerId < :id))"
            + " ORDER BY f.createdAt DESC, f.followerId DESC")
    List<Follow> findFollowersAfter(
            @Param("userId") long userId, @Param("at") Instant at, @Param("id") long id, Limit limit);
}
