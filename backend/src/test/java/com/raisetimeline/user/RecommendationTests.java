package com.raisetimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** おすすめの利用者（F-US-04、DB 設計書 5.7）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecommendationTests {

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

    private List<String> recommendations() throws Exception {
        String body = me.client()
                .get("/api/users/recommendations")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.items[*].username");
    }

    @Test
    void フォロー中の人がフォローしている人を多くフォローされている順に出す() throws Exception {
        long f1 = TestUsers.insert(jdbc, "friend_1");
        long f2 = TestUsers.insert(jdbc, "friend_2");
        long popular = TestUsers.insert(jdbc, "popular");
        long single = TestUsers.insert(jdbc, "single");
        TestUsers.follow(jdbc, me.id(), f1);
        TestUsers.follow(jdbc, me.id(), f2);
        TestUsers.follow(jdbc, f1, popular);
        TestUsers.follow(jdbc, f2, popular); // popular はフォロー中の 2 人からフォローされている
        TestUsers.follow(jdbc, f2, single);
        assertThat(recommendations()).startsWith("popular", "single");
    }

    @Test
    void 自分とフォロー済みの人は出さない() throws Exception {
        long f1 = TestUsers.insert(jdbc, "friend_1");
        long f2 = TestUsers.insert(jdbc, "friend_2");
        TestUsers.follow(jdbc, me.id(), f1);
        TestUsers.follow(jdbc, me.id(), f2);
        TestUsers.follow(jdbc, f1, f2); // f2 はフォロー済み
        TestUsers.follow(jdbc, f1, me.id()); // 自分
        assertThat(recommendations()).doesNotContain("me_user", "friend_1", "friend_2");
    }

    @Test
    void 足りなければ最近登録した人で埋めて最大5人() throws Exception {
        long f1 = TestUsers.insert(jdbc, "friend_1");
        long fof = TestUsers.insert(jdbc, "fof_user");
        TestUsers.follow(jdbc, me.id(), f1);
        TestUsers.follow(jdbc, f1, fof);
        for (int i = 0; i < 6; i++) {
            TestUsers.insert(jdbc, "recent_%d".formatted(i));
        }
        // 1 段目の fof_user のあと、新しく登録した順に 4 人（重複しない）
        assertThat(recommendations()).containsExactly("fof_user", "recent_5", "recent_4", "recent_3", "recent_2");
    }

    @Test
    void 誰もフォローしていなければ最近登録した人を出す() throws Exception {
        TestUsers.insert(jdbc, "old_user");
        TestUsers.insert(jdbc, "new_user");
        assertThat(recommendations()).containsExactly("new_user", "old_user");
    }

    @Test
    void ほかに誰もいなければ空() throws Exception {
        assertThat(recommendations()).isEmpty();
    }
}
