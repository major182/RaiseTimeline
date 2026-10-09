package com.raisetimeline.timeline;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * フォロー中タブの SQL（DB 設計書 5.2・5.3・5.5）。
 *
 * <p>「自分とフォロー中の人の投稿」と「いいね経由の投稿」を UNION ALL でまとめて並べるなど、
 * JPQL では書けない形（WITH・DISTINCT ON・行の組の比較）を使うため、SQL をそのまま書いて実行する。
 * 値はすべてパラメータで渡す（SQL の文字列に利用者の入力をつなげない）。
 */
@Repository
public class TimelineQueries {

    /**
     * フォロー中タブの並び（DB 設計書 5.2）。
     * A：自分とフォロー中の人の投稿（並びの時刻は投稿日時）。
     * B：フォロー中の人がいいねした、フォローしていない人の投稿（並びの時刻は、基準の時刻より前で最も新しい、フォロー中の人のいいね）。
     * A と B は投稿者の条件で分かれるので、同じ投稿が両方に入ることはない。
     */
    private static final String FEED = """
            WITH following AS (
              SELECT followee_id AS user_id FROM follows WHERE follower_id = :me
              UNION ALL SELECT :me
            ),
            feed AS (
              SELECT p.id AS post_id, p.created_at AS sort_at
              FROM posts p
              WHERE p.user_id IN (SELECT user_id FROM following)
              UNION ALL
              SELECT liked.post_id, liked.sort_at FROM (
                SELECT DISTINCT ON (l.post_id) l.post_id, l.created_at AS sort_at
                FROM likes l
                JOIN posts p ON p.id = l.post_id
                WHERE l.user_id IN (SELECT followee_id FROM follows WHERE follower_id = :me)
                  AND l.created_at < :baseTime
                  AND p.user_id NOT IN (SELECT user_id FROM following)
                ORDER BY l.post_id, l.created_at DESC
              ) liked
            )
            SELECT post_id, sort_at FROM feed
            WHERE post_id NOT IN (:excludeIds)
            """;

    private static final String AFTER_CURSOR = " AND (sort_at, post_id) < (:cursorAt, :cursorId)";

    private static final String ORDER_AND_LIMIT = " ORDER BY sort_at DESC, post_id DESC LIMIT :limit";

    /**
     * 留守中のハイライトの候補（DB 設計書 5.3）。前回開いた時刻から今までの、フォロー中の人（自分を除く）の投稿のうち、
     * 「いいねの数 + コメントの数 × 2」が 1 以上のものを、点の多い順（同じ点なら新しい順）に取る。
     */
    private static final String HIGHLIGHTS = """
            SELECT p.id
            FROM posts p
            WHERE p.user_id IN (SELECT followee_id FROM follows WHERE follower_id = :me)
              AND p.created_at >= :since AND p.created_at < :now
              AND (SELECT count(*) FROM likes l WHERE l.post_id = p.id)
                  + (SELECT count(*) FROM comments c WHERE c.post_id = p.id) * 2 >= 1
            ORDER BY (SELECT count(*) FROM likes l WHERE l.post_id = p.id)
                     + (SELECT count(*) FROM comments c WHERE c.post_id = p.id) * 2 DESC,
                     p.created_at DESC, p.id DESC
            LIMIT :limit
            """;

    /** いいね経由の表示に使う、フォロー中の人のいいね（基準の時刻より前）。投稿ごとに新しい順（DB 設計書 5.5）。 */
    private static final String FOLLOWEE_LIKES = """
            SELECT l.post_id, l.user_id
            FROM likes l
            WHERE l.post_id IN (:postIds)
              AND l.user_id IN (SELECT followee_id FROM follows WHERE follower_id = :me)
              AND l.created_at < :baseTime
            ORDER BY l.post_id, l.created_at DESC, l.user_id DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public TimelineQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** フォロー中タブの1行。並びの時刻と投稿の ID。 */
    record FeedRow(long postId, Instant sortAt) {}

    /** フォロー中の人のいいねの1行。 */
    record LikeRow(long postId, long userId) {}

    /**
     * フォロー中タブの投稿を取る。
     *
     * @param after カーソル（最初の読み込みなら null）
     * @param excludeIds 一覧に出さない投稿（ハイライトに出したもの）
     */
    List<FeedRow> feed(long me, Instant baseTime, FeedRow after, Collection<Long> excludeIds, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("me", me)
                .addValue("baseTime", utc(baseTime))
                .addValue("excludeIds", withSentinel(excludeIds))
                .addValue("limit", limit);
        String sql = FEED;
        if (after != null) {
            sql += AFTER_CURSOR;
            params.addValue("cursorAt", utc(after.sortAt())).addValue("cursorId", after.postId());
        }
        return jdbc.query(
                sql + ORDER_AND_LIMIT,
                params,
                (rs, i) -> new FeedRow(
                        rs.getLong("post_id"),
                        rs.getObject("sort_at", OffsetDateTime.class).toInstant()));
    }

    /** 留守中のハイライトにする投稿の ID（点の多い順）。 */
    List<Long> highlights(long me, Instant since, Instant now, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("me", me)
                .addValue("since", utc(since))
                .addValue("now", utc(now))
                .addValue("limit", limit);
        return jdbc.queryForList(HIGHLIGHTS, params, Long.class);
    }

    /** 投稿ごとの、フォロー中の人のいいね（新しい順）。 */
    List<LikeRow> followeeLikes(long me, Collection<Long> postIds, Instant baseTime) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("me", me)
                .addValue("postIds", postIds)
                .addValue("baseTime", utc(baseTime));
        return jdbc.query(
                FOLLOWEE_LIKES, params, (rs, i) -> new LikeRow(rs.getLong("post_id"), rs.getLong("user_id")));
    }

    /** 時刻は時差つき（UTC）で渡す。PostgreSQL の TIMESTAMPTZ と、JVM の時差の設定に左右されずに比べられる。 */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** NOT IN () は SQL として書けないため、空のときは使われない ID（-1）を入れる。 */
    private static List<Long> withSentinel(Collection<Long> ids) {
        List<Long> result = new ArrayList<>(ids);
        result.add(-1L);
        return result;
    }
}
