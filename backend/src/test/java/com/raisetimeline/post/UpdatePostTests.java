package com.raisetimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
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

/** 投稿の編集（F-PO-06、BR-12・15・16、API 設計書 4.4）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UpdatePostTests {

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
        postId = TestPosts.createId(alice.client(), "元の本文");
    }

    private String bodyInDb() {
        return jdbc.queryForObject("SELECT body FROM posts WHERE id = ?", String.class, postId);
    }

    @Test
    void 本人は本文を変えられeditedAtが入る() throws Exception {
        alice.client()
                .patch("/api/posts/" + postId, "{\"body\":\"直した本文\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("直した本文"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty())
                .andExpect(jsonPath("$.isMine").value(true));
        assertThat(bodyInDb()).isEqualTo("直した本文");
    }

    @Test
    void 編集しても投稿日時は変わらない() throws Exception {
        String before = jdbc.queryForObject("SELECT created_at::text FROM posts WHERE id = ?", String.class, postId);
        alice.client().patch("/api/posts/" + postId, "{\"body\":\"直した本文\"}");
        String after = jdbc.queryForObject("SELECT created_at::text FROM posts WHERE id = ?", String.class, postId);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void 他人の投稿は編集できず403() throws Exception {
        bob.client()
                .patch("/api/posts/" + postId, "{\"body\":\"乗っ取り\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(bodyInDb()).isEqualTo("元の本文");
    }

    @Test
    void ない投稿は404() throws Exception {
        alice.client()
                .patch("/api/posts/" + (postId + 1000), "{\"body\":\"本文\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 画像のない投稿で本文を空にするとPOST_EMPTY() throws Exception {
        alice.client()
                .patch("/api/posts/" + postId, "{\"body\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_EMPTY"));
        assertThat(bodyInDb()).isEqualTo("元の本文");
    }

    @Test
    void 本文が280文字を超えると400() throws Exception {
        alice.client()
                .patch("/api/posts/" + postId, "{\"body\":\"" + "あ".repeat(281) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("BODY_TOO_LONG"));
    }

    @Test
    void 本文を送らなければREQUIRED() throws Exception {
        alice.client()
                .patch("/api/posts/" + postId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }
}
