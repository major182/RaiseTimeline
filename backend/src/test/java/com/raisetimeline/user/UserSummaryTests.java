package com.raisetimeline.user;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

/** 利用者の要約（UserSummary。API 設計書 3.1）の中身。フォローの一覧を通して確かめる。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserSummaryTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        alice = TestUsers.signup(mvc, "alice");
        bob = TestUsers.signup(mvc, "bob_b");
        carol = TestUsers.signup(mvc, "carol");
        // bob は alice と carol をフォローしている。alice は carol だけをフォローしている
        bob.client().put("/api/users/" + alice.id() + "/follow", "");
        bob.client().put("/api/users/" + carol.id() + "/follow", "");
        alice.client().put("/api/users/" + carol.id() + "/follow", "");
    }

    @Test
    void 要約の項目を返しメールアドレスは返さない() throws Exception {
        alice.client()
                .get("/api/users/" + alice.id() + "/followers")
                .andExpect(jsonPath("$.items[0].id").value(bob.id()))
                .andExpect(jsonPath("$.items[0].username").value("bob_b"))
                .andExpect(jsonPath("$.items[0].displayName").value("bob_b"))
                .andExpect(jsonPath("$.items[0].avatarUrl").isEmpty())
                .andExpect(jsonPath("$.items[0].bio").value(""))
                .andExpect(jsonPath("$.items[0].email").doesNotExist());
    }

    @Test
    void followedByMeは見ている人から見たフォローの有無() throws Exception {
        // bob がフォローしている人の一覧（alice・carol）を、alice が見る
        alice.client()
                .get("/api/users/" + bob.id() + "/following")
                .andExpect(jsonPath("$.items[?(@.id == " + carol.id() + ")].followedByMe").value(true))
                .andExpect(jsonPath("$.items[?(@.id == " + alice.id() + ")].followedByMe").value(false));
    }

    @Test
    void isMeは見ている人本人のときだけtrue() throws Exception {
        alice.client()
                .get("/api/users/" + bob.id() + "/following")
                .andExpect(jsonPath("$.items[?(@.id == " + alice.id() + ")].isMe").value(true))
                .andExpect(jsonPath("$.items[?(@.id == " + carol.id() + ")].isMe").value(false));
    }
}
