package com.raisetimeline.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** いいね・取り消し（F-LK-01、BR-33・34、API 設計書 4.6）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LikeTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;
    private long postId;
    private String path;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        alice = TestUsers.signup(mvc, "alice");
        bob = TestUsers.signup(mvc, "bob_b");
        postId = TestPosts.createId(alice.client(), "投稿");
        path = "/api/posts/" + postId + "/like";
    }

    private int likeRows() {
        return jdbc.queryForObject("SELECT count(*) FROM likes", Integer.class);
    }

    @Test
    void いいねすると今の状態と数を返す() throws Exception {
        bob.client()
                .put(path, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        alice.client().put(path, "").andExpect(jsonPath("$.likeCount").value(2)); // 自分の投稿にもいいねできる
    }

    @Test
    void 二回いいねしても1回分だけ() throws Exception {
        bob.client().put(path, "");
        bob.client()
                .put(path, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        assertThat(likeRows()).isEqualTo(1);
    }

    @Test
    void 取り消すと数が減る() throws Exception {
        bob.client().put(path, "");
        bob.client()
                .delete(path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
        assertThat(likeRows()).isZero();
    }

    @Test
    void いいねしていなくても取り消しは今の状態を返す() throws Exception {
        alice.client().put(path, "");
        bob.client()
                .delete(path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(1)); // alice のいいねは残る
    }

    @Test
    void ない投稿は404() throws Exception {
        String missing = "/api/posts/" + (postId + 1000) + "/like";
        bob.client().put(missing, "").andExpect(status().isNotFound());
        bob.client().delete(missing).andExpect(status().isNotFound());
    }

    @Test
    void ログインしていなければ401() throws Exception {
        mvc.perform(put(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void 投稿を消すといいねも消える() throws Exception {
        bob.client().put(path, "");
        alice.client().delete("/api/posts/" + postId).andExpect(status().isNoContent());
        assertThat(likeRows()).isZero();
    }
}
