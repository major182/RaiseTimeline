package com.raisetimeline.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** プロフィールの表示（F-US-01、F-FL-04、API 設計書 3.2・4.2）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProfileTests {

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
        other = TestUsers.insert(jdbc, "Other_User", "ほかの人");
    }

    @Test
    void IDでプロフィールを取れる() throws Exception {
        me.client()
                .get("/api/users/" + other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other))
                .andExpect(jsonPath("$.username").value("Other_User"))
                .andExpect(jsonPath("$.displayName").value("ほかの人"))
                .andExpect(jsonPath("$.avatarUrl").isEmpty())
                .andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void ユーザー名は大文字小文字を区別せずに探す() throws Exception {
        me.client()
                .get("/api/users/by-username/other_user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other))
                .andExpect(jsonPath("$.username").value("Other_User")); // 表示は登録したときのまま
    }

    @Test
    void フォロー数とフォロワー数を数える() throws Exception {
        long a = TestUsers.insert(jdbc, "user_a");
        long b = TestUsers.insert(jdbc, "user_b");
        TestUsers.follow(jdbc, other, a); // other → a
        TestUsers.follow(jdbc, a, other); // a → other
        TestUsers.follow(jdbc, b, other); // b → other
        me.client()
                .get("/api/users/" + other)
                .andExpect(jsonPath("$.followingCount").value(1))
                .andExpect(jsonPath("$.followerCount").value(2));
    }

    @Test
    void 自分のプロフィールはisMeがtrue() throws Exception {
        me.client()
                .get("/api/users/" + me.id())
                .andExpect(jsonPath("$.isMe").value(true))
                .andExpect(jsonPath("$.followedByMe").value(false));
    }

    @Test
    void フォローしている人はfollowedByMeがtrue() throws Exception {
        me.client().get("/api/users/" + other).andExpect(jsonPath("$.followedByMe").value(false));
        TestUsers.follow(jdbc, me.id(), other);
        me.client()
                .get("/api/users/" + other)
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.isMe").value(false));
    }

    @Test
    void いない利用者は404を返す() throws Exception {
        me.client()
                .get("/api/users/" + (other + 1000))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        me.client().get("/api/users/by-username/nobody_here").andExpect(status().isNotFound());
    }

    @Test
    void ログインしていなければ401を返す() throws Exception {
        mvc.perform(get("/api/users/" + other)).andExpect(status().isUnauthorized());
    }
}
