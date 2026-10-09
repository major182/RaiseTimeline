package com.raisetimeline.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** フォロー・解除（F-FL-01、API 設計書 4.7）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FollowTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users"); // フォローも ON DELETE CASCADE で消える
        alice = TestUsers.signup(mvc, "alice");
        bob = TestUsers.signup(mvc, "bob_b");
    }

    private int followRows() {
        return jdbc.queryForObject("SELECT count(*) FROM follows", Integer.class);
    }

    @Test
    void フォローすると204を返しフォローの一覧に出る() throws Exception {
        alice.client().put("/api/users/" + bob.id() + "/follow", "").andExpect(status().isNoContent());
        alice.client()
                .get("/api/users/" + alice.id() + "/following")
                .andExpect(jsonPath("$.items[0].id").value(bob.id()))
                .andExpect(jsonPath("$.items[0].followedByMe").value(true));
    }

    @Test
    void 二回フォローしても行は1つで204を返す() throws Exception {
        alice.client().put("/api/users/" + bob.id() + "/follow", "").andExpect(status().isNoContent());
        alice.client().put("/api/users/" + bob.id() + "/follow", "").andExpect(status().isNoContent());
        assertThat(followRows()).isEqualTo(1);
    }

    @Test
    void フォローをやめると行が消える() throws Exception {
        alice.client().put("/api/users/" + bob.id() + "/follow", "");
        alice.client().delete("/api/users/" + bob.id() + "/follow").andExpect(status().isNoContent());
        assertThat(followRows()).isZero();
    }

    @Test
    void フォローしていない人のフォローをやめても204を返す() throws Exception {
        alice.client().delete("/api/users/" + bob.id() + "/follow").andExpect(status().isNoContent());
    }

    @Test
    void フォローの向きは片方だけ() throws Exception {
        alice.client().put("/api/users/" + bob.id() + "/follow", "");
        bob.client().delete("/api/users/" + alice.id() + "/follow"); // bob がやめても alice のフォローは残る
        assertThat(followRows()).isEqualTo(1);
    }

    @Test
    void 自分自身はフォローできず400を返す() throws Exception {
        alice.client()
                .put("/api/users/" + alice.id() + "/follow", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(followRows()).isZero();
    }

    @Test
    void いない利用者は404を返す() throws Exception {
        long missing = bob.id() + 1000;
        alice.client()
                .put("/api/users/" + missing + "/follow", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        alice.client().delete("/api/users/" + missing + "/follow").andExpect(status().isNotFound());
    }

    @Test
    void ログインしていなければ401を返す() throws Exception {
        mvc.perform(put("/api/users/" + bob.id() + "/follow"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 利用者を消すとフォローも消える() throws Exception {
        alice.client().put("/api/users/" + bob.id() + "/follow", "");
        jdbc.update("DELETE FROM users WHERE id = ?", bob.id());
        assertThat(followRows()).isZero();
    }
}
