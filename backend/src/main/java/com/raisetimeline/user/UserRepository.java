package com.raisetimeline.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 利用者の読み書き。
 * ユーザー名・メールアドレスは lower() で比べ、DB の索引（lower(username) など。DB 設計書 4.1）を使えるようにする。
 * Spring Data の IgnoreCase は upper() を使うため、索引が効かない。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);

    /** 自分以外に、このユーザー名を使っている人がいるか（自分の今のユーザー名は「使われている」にしない）。 */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.username) = lower(:username) AND u.id <> :exceptId")
    boolean existsByUsernameExcept(@Param("username") String username, @Param("exceptId") long exceptId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * 利用者の検索（DB 設計書 5.6）。ユーザー名・表示名の部分一致で、大文字・小文字を区別しない。
     * ユーザー名が完全に一致する人を先頭にし、残りは新しく登録した順。
     * pg_trgm の GIN 索引（lower(username) など）が効くよう、lower() の形で比べる。
     *
     * @param escaped LIKE の特別な記号（%・_・\）をエスケープした検索語
     * @param query エスケープしていない検索語（完全一致の判定に使う）
     */
    @Query(
            value = "SELECT * FROM users"
                    + " WHERE lower(username) LIKE '%' || lower(:escaped) || '%' ESCAPE '\\'"
                    + "    OR lower(display_name) LIKE '%' || lower(:escaped) || '%' ESCAPE '\\'"
                    + " ORDER BY (lower(username) = lower(:query)) DESC, id DESC"
                    + " LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<User> search(
            @Param("escaped") String escaped,
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * おすすめの利用者の 1 段目（DB 設計書 5.7）。「フォロー中の人がフォローしている人」のうち、
     * 自分とフォロー済みの人を除き、フォロー中の何人からフォローされているかの多い順。
     */
    @Query(
            value = "WITH mine AS (SELECT followee_id FROM follows WHERE follower_id = :me)"
                    + " SELECT u.* FROM follows f JOIN users u ON u.id = f.followee_id"
                    + " WHERE f.follower_id IN (SELECT followee_id FROM mine)"
                    + "   AND f.followee_id <> :me"
                    + "   AND f.followee_id NOT IN (SELECT followee_id FROM mine)"
                    + " GROUP BY u.id"
                    + " ORDER BY count(*) DESC, u.id DESC"
                    + " LIMIT :limit",
            nativeQuery = true)
    List<User> findFollowedByFollowing(@Param("me") long me, @Param("limit") int limit);

    /**
     * おすすめの利用者の 2 段目（DB 設計書 5.7）。最近登録した人（自分・フォロー済みの人・exclude の人を除く）。
     *
     * @param exclude 1 段目で選んだ人。空の配列だと SQL の NOT IN が書けないので、空のときは使われない ID（-1）を入れて渡す
     */
    @Query(
            value = "SELECT * FROM users"
                    + " WHERE id <> :me"
                    + "   AND id NOT IN (SELECT followee_id FROM follows WHERE follower_id = :me)"
                    + "   AND id NOT IN (:exclude)"
                    + " ORDER BY created_at DESC, id DESC"
                    + " LIMIT :limit",
            nativeQuery = true)
    List<User> findRecentExcept(
            @Param("me") long me, @Param("exclude") Collection<Long> exclude, @Param("limit") int limit);

    /** 最後にフォロー中タブを開いた時刻を記録する（留守中のハイライトのためだけに使う。BR-55）。 */
    @Modifying
    @Query("UPDATE User u SET u.lastTimelineViewedAt = :at WHERE u.id = :id")
    int updateLastTimelineViewedAt(@Param("id") long id, @Param("at") Instant at);
}
