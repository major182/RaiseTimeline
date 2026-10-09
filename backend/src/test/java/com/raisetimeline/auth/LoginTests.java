package com.raisetimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.ApiClient;
import com.raisetimeline.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** ログイン・ログアウト（認証の段階 4）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginTests {

    private static final String CORRECT = """
            {"email":"me@example.com","password":"pass1234"}
            """;
    private static final String WRONG = """
            {"email":"me@example.com","password":"wrong123"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
        new ApiClient(mvc)
                .post(
                        "/api/auth/signup",
                        """
                        {"email":"me@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"raise_me"}
                        """)
                .andExpect(status().isCreated());
        jdbc.update("DELETE FROM spring_session"); // 登録でできたセッションは使わない
    }

    @Test
    void 正しいメールアドレスとパスワードでログインできる() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("raise_me"));
        client.get("/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void メールアドレスは大文字小文字を区別しない() throws Exception {
        new ApiClient(mvc)
                .post("/api/auth/login", """
                        {"email":"ME@Example.COM","password":"pass1234"}
                        """)
                .andExpect(status().isOk());
    }

    @Test
    void パスワード違いと未登録のメールアドレスは同じ失敗を返す() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", WRONG)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.detail").value("メールアドレスかパスワードが違います"));
        client.post("/api/auth/login", """
                        {"email":"nobody@example.com","password":"pass1234"}
                        """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        client.get("/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void 五回続けて失敗するとログインが止まり正しいパスワードでも入れない() throws Exception {
        ApiClient client = new ApiClient(mvc);
        for (int i = 1; i <= 4; i++) {
            client.post("/api/auth/login", WRONG).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        client.post("/api/auth/login", WRONG).andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
        client.post("/api/auth/login", CORRECT)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
        Integer minutes = jdbc.queryForObject(
                "SELECT round(extract(epoch FROM locked_until - now()) / 60)::int FROM users", Integer.class);
        assertThat(minutes).isEqualTo(15);
    }

    @Test
    void 止める時間が過ぎれば正しいパスワードでログインできる() throws Exception {
        jdbc.update("UPDATE users SET locked_until = now() - interval '1 minute'");
        new ApiClient(mvc).post("/api/auth/login", CORRECT).andExpect(status().isOk());
    }

    @Test
    void ログインに成功すると失敗の回数が0に戻る() throws Exception {
        ApiClient client = new ApiClient(mvc);
        for (int i = 1; i <= 4; i++) {
            client.post("/api/auth/login", WRONG);
        }
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM users", Integer.class))
                .isZero();
    }

    @Test
    void ログインするとセッションIDとCSRFトークンが新しくなる() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        String sessionBefore = client.cookie("SESSION").getValue();
        String tokenBefore = client.cookie("XSRF-TOKEN").getValue();

        // セッションを持った状態でもう一度ログインする（ログイン前のセッション ID を知られていた場合を想定）
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());

        assertThat(client.cookie("SESSION").getValue()).isNotEqualTo(sessionBefore);
        assertThat(client.cookie("XSRF-TOKEN").getValue()).isNotEqualTo(tokenBefore);
        client.get("/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void ログアウトするとすぐにログインしていない状態になりDBのセッションも消える() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class))
                .isEqualTo(1);

        client.post("/api/auth/logout", "").andExpect(status().isNoContent());

        assertThat(client.cookie("SESSION")).isNull(); // Cookie を消す指示が返っている
        assertThat(client.cookie("XSRF-TOKEN")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class))
                .isZero();
        client.get("/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void ログインしていない呼び出しではセッションを作らない() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.get("/api/auth/me").andExpect(status().isUnauthorized());
        client.post("/api/auth/login", WRONG).andExpect(status().isUnauthorized());
        assertThat(client.cookie("SESSION")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class))
                .isZero();
    }

    @Test
    void ログインしていなければログアウトは401を返す() throws Exception {
        new ApiClient(mvc).post("/api/auth/logout", "").andExpect(status().isUnauthorized());
    }

    @Test
    void 空のメールアドレスとパスワードは入力してくださいを返す() throws Exception {
        new ApiClient(mvc)
                .post("/api/auth/login", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }
}
