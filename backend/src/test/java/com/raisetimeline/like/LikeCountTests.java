package com.raisetimeline.like;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

/** 投稿カードのいいねの数といいね済みか（F-LK-02、BR-32）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LikeCountTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        alice = TestUsers.signup(mvc, "alice");
        bob = TestUsers.signup(mvc, "bob_b");
    }

    @Test
    void いいねの数を投稿ごとに数える() throws Exception {
        long first = TestPosts.createId(alice.client(), "1つ目");
        long second = TestPosts.createId(alice.client(), "2つ目");
        alice.client().put("/api/posts/" + first + "/like", "");
        bob.client().put("/api/posts/" + first + "/like", "");
        alice.client().get("/api/posts/" + first).andExpect(jsonPath("$.likeCount").value(2));
        alice.client().get("/api/posts/" + second).andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    void likedByMeは見ている人ごとに違う() throws Exception {
        long postId = TestPosts.createId(alice.client(), "投稿");
        bob.client().put("/api/posts/" + postId + "/like", "");
        bob.client().get("/api/posts/" + postId).andExpect(jsonPath("$.likedByMe").value(true));
        alice.client().get("/api/posts/" + postId).andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    void 取り消すとlikedByMeがfalseに戻る() throws Exception {
        long postId = TestPosts.createId(alice.client(), "投稿");
        bob.client().put("/api/posts/" + postId + "/like", "");
        bob.client().delete("/api/posts/" + postId + "/like");
        bob.client()
                .get("/api/posts/" + postId)
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
    }
}
