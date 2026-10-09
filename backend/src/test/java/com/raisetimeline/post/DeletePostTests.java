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

/** 投稿の削除（F-PO-03、BR-12・13、API 設計書 4.4）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeletePostTests {

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
        postId = TestPosts.createId(alice.client(), "消す投稿");
    }

    private boolean exists() {
        return jdbc.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, postId) == 1;
    }

    @Test
    void 本人は削除でき以後は404() throws Exception {
        alice.client().delete("/api/posts/" + postId).andExpect(status().isNoContent());
        assertThat(exists()).isFalse();
        alice.client().get("/api/posts/" + postId).andExpect(status().isNotFound());
    }

    @Test
    void 他人の投稿は削除できず403() throws Exception {
        bob.client()
                .delete("/api/posts/" + postId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(exists()).isTrue();
    }

    @Test
    void ない投稿は404() throws Exception {
        alice.client().delete("/api/posts/" + (postId + 1000)).andExpect(status().isNotFound());
    }

    @Test
    void 二回目の削除は404() throws Exception {
        alice.client().delete("/api/posts/" + postId);
        alice.client().delete("/api/posts/" + postId).andExpect(status().isNotFound());
    }

    @Test
    void ほかの投稿は消えない() throws Exception {
        long other = TestPosts.createId(alice.client(), "残す投稿");
        alice.client().delete("/api/posts/" + postId);
        alice.client().get("/api/posts/" + other).andExpect(status().isOk());
    }
}
