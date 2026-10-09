package com.raisetimeline.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.ApiClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** タイムラインのテストの道具。 */
final class Timelines {

    /** テストの投稿日時の基準。今より前にしておく（未来の時刻だと、いいね経由の基準の時刻より後になるため）。 */
    static final Instant BASE = Instant.now().minusSeconds(86_400);

    private Timelines() {}

    /** 1 ページ分の応答。 */
    record Page(List<Long> highlights, List<Long> items, String nextCursor, String json) {}

    static Page read(ApiClient client, String path, String cursor) throws Exception {
        String json = client.get(path, "cursor", cursor)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Long> highlights = json.contains("\"highlights\"") ? ids(json, "$.highlights[*].id") : List.of();
        List<Long> items = ids(json, "$.items[*].id");
        assertThat(items.size()).isLessThanOrEqualTo(20);
        return new Page(highlights, items, JsonPath.read(json, "$.nextCursor"), json);
    }

    /** 一覧を最後まで読み、出てきた投稿の ID を順番に返す（ハイライトは含めない）。 */
    static List<Long> readAll(ApiClient client, String path) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        do {
            Page page = read(client, path, cursor);
            ids.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        return ids;
    }

    static void like(JdbcTemplate jdbc, long postId, long userId, Instant at) {
        jdbc.update(
                "INSERT INTO likes (post_id, user_id, created_at) VALUES (?, ?, ?)", postId, userId, Timestamp.from(at));
    }

    static void comment(JdbcTemplate jdbc, long postId, long userId) {
        jdbc.update("INSERT INTO comments (post_id, user_id, body) VALUES (?, ?, 'コメント')", postId, userId);
    }

    static void setLastViewed(JdbcTemplate jdbc, long userId, Instant at) {
        jdbc.update("UPDATE users SET last_timeline_viewed_at = ? WHERE id = ?", Timestamp.from(at), userId);
    }

    private static List<Long> ids(String json, String path) {
        List<Number> numbers = JsonPath.read(json, path);
        return numbers.stream().map(Number::longValue).toList();
    }
}
