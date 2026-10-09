package com.raisetimeline.timeline;

import static com.raisetimeline.timeline.Timelines.BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 利用者ごとの投稿一覧（F-TL-03、DB 設計書 5.4）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserPostsTimelineTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private long other;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
        other = TestUsers.insert(jdbc, "other");
    }

    @Test
    void その利用者の投稿だけを新しい順に出す() throws Exception {
        long a = TestPosts.insert(jdbc, other, "1", BASE.plusSeconds(1));
        TestPosts.insert(jdbc, me.id(), "自分", BASE.plusSeconds(2));
        long b = TestPosts.insert(jdbc, other, "3", BASE.plusSeconds(3));
        assertThat(Timelines.readAll(me.client(), "/api/users/" + other + "/posts")).containsExactly(b, a);
    }

    @Test
    void 二十件を超えると続きを読める() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            expected.addFirst(TestPosts.insert(jdbc, other, "p" + i, BASE.plusSeconds(i)));
            TestPosts.insert(jdbc, me.id(), "自分" + i, BASE.plusSeconds(i)); // 混ざらないこと
        }
        assertThat(Timelines.readAll(me.client(), "/api/users/" + other + "/posts"))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void 投稿がなければ空() throws Exception {
        assertThat(Timelines.readAll(me.client(), "/api/users/" + other + "/posts")).isEmpty();
    }

    @Test
    void いない利用者は404() throws Exception {
        me.client().get("/api/users/" + (other + 1000) + "/posts").andExpect(status().isNotFound());
    }
}
