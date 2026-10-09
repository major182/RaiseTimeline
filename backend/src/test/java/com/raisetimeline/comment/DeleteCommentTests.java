package com.raisetimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/** コメントの削除と、消せる人（F-CM-04、BR-13・35）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeleteCommentTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** 投稿した人。 */
    private TestUser owner;

    /** コメントした人。 */
    private TestUser commenter;

    /** どちらでもない人。 */
    private TestUser stranger;

    private long postId;
    private long commentId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        owner = TestUsers.signup(mvc, "owner");
        commenter = TestUsers.signup(mvc, "commenter");
        stranger = TestUsers.signup(mvc, "stranger");
        postId = TestPosts.createId(owner.client(), "投稿");
        String json = commenter
                .client()
                .post("/api/posts/" + postId + "/comments", "{\"body\":\"コメント\"}")
                .andReturn()
                .getResponse()
                .getContentAsString();
        commentId = ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private boolean exists() {
        return jdbc.queryForObject("SELECT count(*) FROM comments WHERE id = ?", Integer.class, commentId) == 1;
    }

    @Test
    void コメントした本人は消せる() throws Exception {
        commenter.client().delete("/api/comments/" + commentId).andExpect(status().isNoContent());
        assertThat(exists()).isFalse();
    }

    @Test
    void 投稿した本人も他人のコメントを消せる() throws Exception {
        owner.client().delete("/api/comments/" + commentId).andExpect(status().isNoContent());
        assertThat(exists()).isFalse();
    }

    @Test
    void どちらでもない人は消せず403() throws Exception {
        stranger.client()
                .delete("/api/comments/" + commentId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(exists()).isTrue();
    }

    @Test
    void deletableは消せる人にだけtrue() throws Exception {
        String path = "/api/posts/" + postId + "/comments";
        commenter.client().get(path).andExpect(jsonPath("$.items[0].deletable").value(true));
        owner.client().get(path).andExpect(jsonPath("$.items[0].deletable").value(true));
        stranger.client().get(path).andExpect(jsonPath("$.items[0].deletable").value(false));
    }

    @Test
    void ないコメントは404() throws Exception {
        commenter.client().delete("/api/comments/" + (commentId + 1000)).andExpect(status().isNotFound());
    }

    @Test
    void 投稿を消すとコメントも消える() throws Exception {
        owner.client().delete("/api/posts/" + postId).andExpect(status().isNoContent());
        assertThat(exists()).isFalse();
    }
}
