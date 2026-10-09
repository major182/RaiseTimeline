package com.raisetimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** コメントの投稿（F-CM-01、BR-30、API 設計書 4.5）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CreateCommentTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        alice = TestUsers.signup(mvc, "alice");
        bob = TestUsers.signup(mvc, "bob_b");
        postId = TestPosts.createId(alice.client(), "alice の投稿");
    }

    private int commentRows() {
        return jdbc.queryForObject("SELECT count(*) FROM comments", Integer.class);
    }

    private String json(String body) {
        return "{\"body\":\"" + body + "\"}";
    }

    @Test
    void コメントすると201とコメントを返す() throws Exception {
        bob.client()
                .post("/api/posts/" + postId + "/comments", json("いいですね"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.matchesPattern("/api/comments/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.author.id").value(bob.id()))
                .andExpect(jsonPath("$.body").value("いいですね"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.deletable").value(true));
        assertThat(commentRows()).isEqualTo(1);
    }

    @Test
    void 本文は1から280文字で絵文字は1文字に数える() throws Exception {
        bob.client()
                .post("/api/posts/" + postId + "/comments", json("😀".repeat(280)))
                .andExpect(status().isCreated());
        bob.client()
                .post("/api/posts/" + postId + "/comments", json("あ".repeat(281)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].code").value("BODY_TOO_LONG"));
        assertThat(commentRows()).isEqualTo(1);
    }

    @Test
    void 空や空白だけはREQUIRED() throws Exception {
        for (String body : new String[] {"{\"body\":\"\"}", "{\"body\":\"   \"}", "{}"}) {
            bob.client()
                    .post("/api/posts/" + postId + "/comments", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        }
        assertThat(commentRows()).isZero();
    }

    @Test
    void ない投稿へのコメントは404() throws Exception {
        bob.client()
                .post("/api/posts/" + (postId + 1000) + "/comments", json("こんにちは"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 自分の投稿にもコメントできる() throws Exception {
        alice.client()
                .post("/api/posts/" + postId + "/comments", json("補足です"))
                .andExpect(status().isCreated());
    }
}
