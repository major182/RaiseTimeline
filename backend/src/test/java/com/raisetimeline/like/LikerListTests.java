package com.raisetimeline.like;

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

/** いいねした人の一覧（F-LK-03、API 設計書 4.6）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LikerListTests {

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

    private void insertLike(long userId, Instant at) {
        jdbc.update(
                "INSERT INTO likes (post_id, user_id, created_at) VALUES (?, ?, ?)", postId, userId, Timestamp.from(at));
    }

    private List<Long> readAll() throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        do {
            String json = me.client()
                    .get("/api/posts/" + postId + "/likes", "cursor", cursor)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            List<Number> page = JsonPath.read(json, "$.items[*].id");
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(json, "$.nextCursor");
        } while (cursor != null);
        return ids;
    }

    @Test
    void いいねした時刻の新しい順() throws Exception {
        long a = TestUsers.insert(jdbc, "user_a");
        long b = TestUsers.insert(jdbc, "user_b");
        insertLike(a, BASE.plusSeconds(2));
        insertLike(b, BASE.plusSeconds(1));
        insertLike(me.id(), BASE.plusSeconds(3));
        assertThat(readAll()).containsExactly(me.id(), a, b);
    }

    @Test
    void 二十件を超えると続きを読め重複も欠落もしない() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            long id = TestUsers.insert(jdbc, "liker_%02d".formatted(i));
            insertLike(id, i < 5 ? BASE : BASE.plusSeconds(i)); // 最初の 5 人は同じ時刻
            expected.addFirst(id);
        }
        assertThat(readAll()).containsExactlyElementsOf(expected);
    }

    @Test
    void いいねがなければ空() throws Exception {
        me.client()
                .get("/api/posts/" + postId + "/likes")
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void 利用者の要約で返す() throws Exception {
        long a = TestUsers.insert(jdbc, "user_a");
        TestUsers.follow(jdbc, me.id(), a);
        insertLike(a, BASE);
        me.client()
                .get("/api/posts/" + postId + "/likes")
                .andExpect(jsonPath("$.items[0].username").value("user_a"))
                .andExpect(jsonPath("$.items[0].followedByMe").value(true));
    }

    @Test
    void ない投稿は404() throws Exception {
        me.client().get("/api/posts/" + (postId + 1000) + "/likes").andExpect(status().isNotFound());
    }
}
