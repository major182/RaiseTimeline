package com.raisetimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** コメントの一覧（F-CM-02、API 設計書 4.5）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommentListTests {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
        postId = TestPosts.createId(me.client(), "投稿");
    }

    private long insertComment(long post, String body, Instant at) {
        return jdbc.queryForObject(
                "INSERT INTO comments (post_id, user_id, body, created_at) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                post,
                me.id(),
                body,
                Timestamp.from(at));
    }

    private List<String> readAll() throws Exception {
        List<String> bodies = new ArrayList<>();
        String cursor = null;
        do {
            String json = me.client()
                    .get("/api/posts/" + postId + "/comments", "cursor", cursor)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            bodies.addAll(JsonPath.read(json, "$.items[*].body"));
            cursor = JsonPath.read(json, "$.nextCursor");
        } while (cursor != null);
        return bodies;
    }

    @Test
    void コメントは古い順() throws Exception {
        insertComment(postId, "2番目", BASE.plusSeconds(2));
        insertComment(postId, "1番目", BASE.plusSeconds(1));
        insertComment(postId, "3番目", BASE.plusSeconds(3));
        assertThat(readAll()).containsExactly("1番目", "2番目", "3番目");
    }

    @Test
    void 二十件を超えると続きを読め同じ時刻でも重複欠落しない() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String body = "c%02d".formatted(i);
            insertComment(postId, body, i < 10 ? BASE : BASE.plusSeconds(i)); // 最初の 10 件は同じ時刻
            expected.add(body);
        }
        assertThat(readAll()).containsExactlyElementsOf(expected);
    }

    @Test
    void ほかの投稿のコメントは出さない() throws Exception {
        long other = TestPosts.createId(me.client(), "別の投稿");
        insertComment(other, "別の投稿へのコメント", BASE);
        insertComment(postId, "この投稿へのコメント", BASE);
        assertThat(readAll()).containsExactly("この投稿へのコメント");
    }

    @Test
    void コメントがなければ空() throws Exception {
        me.client()
                .get("/api/posts/" + postId + "/comments")
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void ない投稿のコメントは404() throws Exception {
        me.client().get("/api/posts/" + (postId + 1000) + "/comments").andExpect(status().isNotFound());
    }

    @Test
    void 壊れたカーソルは400() throws Exception {
        me.client()
                .get("/api/posts/" + postId + "/comments", "cursor", "@@@")
                .andExpect(status().isBadRequest());
    }
}
