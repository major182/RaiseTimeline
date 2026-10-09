package com.raisetimeline.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.ApiClient;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** テスト用の投稿を作る道具。 */
public final class TestPosts {

    private static final MockMultipartFile[] NO_FILES = {};

    private TestPosts() {}

    /** API で本文だけの投稿をする（応答をそのまま返す）。 */
    public static ResultActions create(ApiClient client, String body) throws Exception {
        return client.multipart("/api/posts", NO_FILES, "body", body);
    }

    /** API で投稿し、投稿の ID を返す。 */
    public static long createId(ApiClient client, String body) throws Exception {
        String json = create(client, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** 投稿を DB に直接作る（投稿日時を決めたいとき）。ID を返す。 */
    public static long insert(JdbcTemplate jdbc, long userId, String body, Instant createdAt) {
        return jdbc.queryForObject(
                "INSERT INTO posts (user_id, body, created_at) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                userId,
                body,
                Timestamp.from(createdAt));
    }
}
