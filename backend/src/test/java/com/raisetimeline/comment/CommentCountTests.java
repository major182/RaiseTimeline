package com.raisetimeline.comment;

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

/** 投稿カードのコメントの数（F-CM-03、BR-31）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommentCountTests {

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

    private void comment(long postId) throws Exception {
        me.client().post("/api/posts/" + postId + "/comments", "{\"body\":\"コメント\"}");
    }

    @Test
    void コメントの数を投稿ごとに数える() throws Exception {
        long first = TestPosts.createId(me.client(), "1つ目");
        long second = TestPosts.createId(me.client(), "2つ目");
        comment(first);
        comment(first);
        comment(second);
        me.client().get("/api/posts/" + first).andExpect(jsonPath("$.commentCount").value(2));
        me.client().get("/api/posts/" + second).andExpect(jsonPath("$.commentCount").value(1));
    }

    @Test
    void コメントを消すと数が減る() throws Exception {
        long postId = TestPosts.createId(me.client(), "投稿");
        comment(postId);
        long commentId = jdbc.queryForObject("SELECT id FROM comments", Long.class);
        me.client().delete("/api/comments/" + commentId);
        me.client().get("/api/posts/" + postId).andExpect(jsonPath("$.commentCount").value(0));
    }
}
