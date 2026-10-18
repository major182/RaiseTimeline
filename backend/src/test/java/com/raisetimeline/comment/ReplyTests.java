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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** コメントへの返信（F-CM-05、F-CM-06、BR-31、BR-37、BR-38、API 設計書 4.5）。 */
@DisplayName("IT-CM-02 返信")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReplyTests {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser owner;
    private TestUser commenter;
    private long postId;
    private long commentId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        owner = TestUsers.signup(mvc, "owner");
        commenter = TestUsers.signup(mvc, "commenter");
        postId = TestPosts.createId(owner.client(), "投稿");
        commentId = post(commenter, postId, "コメント", null);
    }

    /** コメント・返信して ID を返す。 */
    private long post(TestUser user, long post, String body, Long parentId) throws Exception {
        String json = user.client()
                .post("/api/posts/" + post + "/comments", body(body, parentId))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String body(String body, Long parentId) {
        return parentId == null
                ? "{\"body\":\"" + body + "\"}"
                : "{\"body\":\"" + body + "\",\"parentId\":" + parentId + "}";
    }

    private void insertReply(String body, Instant at) {
        jdbc.update(
                "INSERT INTO comments (post_id, user_id, parent_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
                postId,
                owner.id(),
                commentId,
                body,
                Timestamp.from(at));
    }

    private List<String> readAllReplies() throws Exception {
        List<String> bodies = new ArrayList<>();
        String cursor = null;
        do {
            String json = owner.client()
                    .get("/api/comments/" + commentId + "/replies", "cursor", cursor)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            bodies.addAll(JsonPath.read(json, "$.items[*].body"));
            cursor = JsonPath.read(json, "$.nextCursor");
        } while (cursor != null);
        return bodies;
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM comments", Integer.class);
    }

    @Test
    void 返信すると201と返信先を返す() throws Exception {
        owner.client()
                .post("/api/posts/" + postId + "/comments", body("ありがとう", commentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(commentId))
                .andExpect(jsonPath("$.replyCount").value(0))
                .andExpect(jsonPath("$.deletable").value(true));
    }

    @Test
    void 返信への返信は元のコメントへの返信になる() throws Exception {
        long reply = post(owner, postId, "返信", commentId);
        commenter
                .client()
                .post("/api/posts/" + postId + "/comments", body("@owner 返信への返信", reply))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(commentId));
    }

    @Test
    void 返信先がない別の投稿のコメントなら404で保存しない() throws Exception {
        long other = TestPosts.createId(owner.client(), "別の投稿");
        long otherComment = post(owner, other, "別の投稿へのコメント", null);
        int before = rows();
        for (long parentId : new long[] {commentId + 1000, otherComment}) {
            owner.client()
                    .post("/api/posts/" + postId + "/comments", body("返信", parentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void コメントの一覧は返信を含めず返信の数を付ける() throws Exception {
        long second = post(owner, postId, "返信のないコメント", null);
        post(owner, postId, "返信1", commentId);
        post(commenter, postId, "返信2", commentId);

        owner.client()
                .get("/api/posts/" + postId + "/comments")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(commentId))
                .andExpect(jsonPath("$.items[0].parentId").isEmpty())
                .andExpect(jsonPath("$.items[0].replyCount").value(2))
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[1].replyCount").value(0));
    }

    @Test
    void 返信の一覧は古い順で二十件を超えると続きを読める() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            String body = "r%02d".formatted(i);
            insertReply(body, i < 5 ? BASE : BASE.plusSeconds(i)); // 最初の 5 件は同じ時刻
            expected.add(body);
        }
        assertThat(readAllReplies()).containsExactlyElementsOf(expected);
    }

    @Test
    void ないコメントの返信の一覧は404() throws Exception {
        owner.client().get("/api/comments/" + (commentId + 1000) + "/replies").andExpect(status().isNotFound());
    }

    @Test
    void 投稿のコメントの数は返信を含む() throws Exception {
        post(owner, postId, "返信", commentId);
        owner.client().get("/api/posts/" + postId).andExpect(jsonPath("$.commentCount").value(2));
    }

    @Test
    void コメントを消すと返信も消え返信を消してもコメントは残る() throws Exception {
        long reply1 = post(owner, postId, "返信1", commentId);
        post(owner, postId, "返信2", commentId);

        owner.client().delete("/api/comments/" + reply1).andExpect(status().isNoContent());
        assertThat(rows()).isEqualTo(2); // コメントと返信2

        commenter.client().delete("/api/comments/" + commentId).andExpect(status().isNoContent());
        assertThat(rows()).isZero();
    }
}
