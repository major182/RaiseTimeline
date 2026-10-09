package com.raisetimeline.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 投稿の詳細（F-PO-04、API 設計書 4.4）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GetPostTests {

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

    @Test
    void 自分の投稿はisMineがtrue() throws Exception {
        alice.client()
                .get("/api/posts/" + postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.body").value("alice の投稿"))
                .andExpect(jsonPath("$.isMine").value(true));
    }

    @Test
    void 他人の投稿も見られてisMineはfalse() throws Exception {
        bob.client()
                .get("/api/posts/" + postId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMine").value(false))
                .andExpect(jsonPath("$.author.id").value(alice.id()))
                .andExpect(jsonPath("$.author.isMe").value(false))
                .andExpect(jsonPath("$.author.followedByMe").value(false));
    }

    @Test
    void 投稿者をフォローしていればauthorのfollowedByMeがtrue() throws Exception {
        TestUsers.follow(jdbc, bob.id(), alice.id());
        bob.client().get("/api/posts/" + postId).andExpect(jsonPath("$.author.followedByMe").value(true));
    }

    @Test
    void 投稿者が表示名を変えたら新しい表示名で返す() throws Exception {
        alice.client().patch("/api/me/profile", "{\"displayName\":\"アリス\"}");
        bob.client().get("/api/posts/" + postId).andExpect(jsonPath("$.author.displayName").value("アリス"));
    }

    @Test
    void ない投稿は404() throws Exception {
        bob.client()
                .get("/api/posts/" + (postId + 1000))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
