package com.raisetimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** 投稿の作成（F-PO-01、BR-10・11、API 設計書 4.4）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CreatePostTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users"); // 投稿も ON DELETE CASCADE で消える
        me = TestUsers.signup(mvc, "me_user");
    }

    private int postRows() {
        return jdbc.queryForObject("SELECT count(*) FROM posts", Integer.class);
    }

    @Test
    void 投稿すると201と投稿カードを返す() throws Exception {
        TestPosts.create(me.client(), "はじめての投稿")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/posts/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.body").value("はじめての投稿"))
                .andExpect(jsonPath("$.author.id").value(me.id()))
                .andExpect(jsonPath("$.author.username").value("me_user"))
                .andExpect(jsonPath("$.author.isMe").value(true))
                .andExpect(jsonPath("$.images").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.editedAt").isEmpty())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.likedVia").isEmpty())
                .andExpect(jsonPath("$.isMine").value(true));
        assertThat(postRows()).isEqualTo(1);
    }

    @Test
    void インプレッション数とリツイートの数は返さない() throws Exception {
        TestPosts.create(me.client(), "本文")
                .andExpect(jsonPath("$.impressionCount").doesNotExist())
                .andExpect(jsonPath("$.retweetCount").doesNotExist()); // BR-40、BR-41
    }

    @Test
    void 本文は280文字までで絵文字は1文字に数える() throws Exception {
        String max = "😀".repeat(280);
        TestPosts.create(me.client(), max).andExpect(status().isCreated()).andExpect(jsonPath("$.body").value(max));
        TestPosts.create(me.client(), "あ".repeat(281))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].code").value("BODY_TOO_LONG"));
        assertThat(postRows()).isEqualTo(1);
    }

    @Test
    void 本文が空か空白だけならPOST_EMPTY() throws Exception {
        for (String body : new String[] {"", "   ", "\n\n"}) {
            TestPosts.create(me.client(), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("POST_EMPTY"))
                    .andExpect(jsonPath("$.detail").value("入力してください"));
        }
        assertThat(postRows()).isZero();
    }

    @Test
    void 本文を送らなければPOST_EMPTY() throws Exception {
        me.client()
                .multipart("/api/posts", new MockMultipartFile[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_EMPTY"));
    }

    @Test
    void 改行や前後の空白はそのまま保存する() throws Exception {
        TestPosts.create(me.client(), "  1行目\n2行目  ").andExpect(jsonPath("$.body").value("  1行目\n2行目  "));
    }

    @Test
    void ログインしていなければ401() throws Exception {
        mvc.perform(multipart("/api/posts").param("body", "本文")).andExpect(status().isUnauthorized());
        assertThat(postRows()).isZero();
    }
}
