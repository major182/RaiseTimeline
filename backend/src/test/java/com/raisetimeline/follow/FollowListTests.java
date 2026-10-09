package com.raisetimeline.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
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

/** フォロー／フォロワーの一覧の並びと続きの読み込み（F-FL-02・03、BR-44、BR-53）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FollowListTests {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
    }

    /** 利用者を DB に直接作る（BCrypt を通さないので速い）。ID を返す。 */
    private long insertUser(String username) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, display_name, email, password_hash) VALUES (?, ?, ?, 'x') RETURNING id",
                Long.class,
                username,
                username,
                username + "@example.com");
    }

    private void insertFollow(long followerId, long followeeId, Instant at) {
        jdbc.update(
                "INSERT INTO follows (follower_id, followee_id, created_at) VALUES (?, ?, ?)",
                followerId,
                followeeId,
                Timestamp.from(at));
    }

    /** 一覧を最後まで読み、出てきた利用者の ID を順番に返す。 */
    private List<Long> readAll(String path) throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        do {
            String body = me.client()
                    .get(cursor == null ? path : path + "?cursor=" + cursor)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            List<Number> page = JsonPath.read(body, "$.items[*].id");
            assertThat(page.size()).isLessThanOrEqualTo(20);
            page.forEach(n -> ids.add(n.longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);
        return ids;
    }

    @Test
    void フォローの一覧はフォローした時刻の新しい順() throws Exception {
        long a = insertUser("user_a");
        long b = insertUser("user_b");
        long c = insertUser("user_c");
        insertFollow(me.id(), a, BASE.plusSeconds(2));
        insertFollow(me.id(), b, BASE.plusSeconds(3));
        insertFollow(me.id(), c, BASE.plusSeconds(1));
        assertThat(readAll("/api/users/" + me.id() + "/following")).containsExactly(b, a, c);
    }

    @Test
    void フォロワーの一覧はフォローされた時刻の新しい順() throws Exception {
        long a = insertUser("user_a");
        long b = insertUser("user_b");
        insertFollow(a, me.id(), BASE.plusSeconds(1));
        insertFollow(b, me.id(), BASE.plusSeconds(2));
        assertThat(readAll("/api/users/" + me.id() + "/followers")).containsExactly(b, a);
    }

    @Test
    void 二十件を超えると続きがあり重複も欠落もしない() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            long id = insertUser("user_%02d".formatted(i));
            insertFollow(me.id(), id, BASE.plusSeconds(i));
            expected.addFirst(id); // 新しい順
        }
        assertThat(readAll("/api/users/" + me.id() + "/following")).containsExactlyElementsOf(expected);
    }

    @Test
    void 同じ時刻のフォローはIDの大きい順で重複も欠落もしない() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            long id = insertUser("same_%02d".formatted(i));
            insertFollow(me.id(), id, BASE); // すべて同じ時刻
            expected.addFirst(id);
        }
        assertThat(readAll("/api/users/" + me.id() + "/following")).containsExactlyElementsOf(expected);
    }

    @Test
    void ちょうど二十件なら続きはない() throws Exception {
        for (int i = 0; i < 20; i++) {
            insertFollow(me.id(), insertUser("user_%02d".formatted(i)), BASE.plusSeconds(i));
        }
        me.client()
                .get("/api/users/" + me.id() + "/following")
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void 誰もいなければ空の一覧を返す() throws Exception {
        me.client()
                .get("/api/users/" + me.id() + "/followers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void 他人の一覧も見られる() throws Exception {
        long a = insertUser("user_a");
        long b = insertUser("user_b");
        insertFollow(a, b, BASE);
        assertThat(readAll("/api/users/" + a + "/following")).containsExactly(b);
        assertThat(readAll("/api/users/" + b + "/followers")).containsExactly(a);
    }

    @Test
    void 壊れたカーソルは400を返す() throws Exception {
        me.client()
                .get("/api/users/" + me.id() + "/following?cursor=broken!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void いない利用者の一覧は404を返す() throws Exception {
        me.client().get("/api/users/" + (me.id() + 1000) + "/following").andExpect(status().isNotFound());
        me.client().get("/api/users/" + (me.id() + 1000) + "/followers").andExpect(status().isNotFound());
    }
}
