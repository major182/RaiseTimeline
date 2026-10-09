package com.raisetimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.ApiClient;
import com.raisetimeline.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** ログイン・取り直し・ログアウト（JWT 方式。API 設計書 4.1）。 */
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
        jdbc.update("DELETE FROM users");
        new ApiClient(mvc)
                .post(
                        "/api/auth/signup",
                        """
                        {"email":"me@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"raise_me"}
                        """)
                .andExpect(status().isCreated());
        jdbc.update("DELETE FROM refresh_tokens"); // 登録で発行したトークンは使わない
    }

    private int activeTokens() {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
    }

    @Test
    void 正しいメールアドレスとパスワードでログインできトークンでmeを取れる() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("raise_me"));
        client.get("/api/auth/me").andExpect(status().isOk());
        assertThat(activeTokens()).isEqualTo(1);
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
    void パスワード違いと未登録のメールアドレスは同じ失敗を返しトークンを発行しない() throws Exception {
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
        assertThat(client.cookie("REFRESH_TOKEN")).isNull();
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
    void 取り直すと新しいアクセストークンとリフレッシュトークンが返り古いリフレッシュトークンは無効になる() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        String refreshBefore = client.cookie("REFRESH_TOKEN").getValue();
        client.setAccessToken(null); // 再読み込みでメモリのアクセストークンが消えた状態

        client.post("/api/auth/refresh")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("raise_me"));

        assertThat(client.cookie("REFRESH_TOKEN").getValue()).isNotEqualTo(refreshBefore);
        client.get("/api/auth/me").andExpect(status().isOk());
        assertThat(activeTokens()).isEqualTo(1); // 古いものは無効、新しいものだけが有効
    }

    @Test
    void 無効にしたリフレッシュトークンが使われたらその利用者のトークンをすべて無効にする() throws Exception {
        ApiClient victim = new ApiClient(mvc);
        victim.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        Cookie stolen = victim.cookie("REFRESH_TOKEN");
        victim.post("/api/auth/refresh").andExpect(status().isOk()); // 本人が取り直す（stolen は無効になる）

        // 盗んだ古いトークンで取り直そうとする
        ApiClient attacker = new ApiClient(mvc);
        attacker.setCookie(stolen);
        attacker.post("/api/auth/refresh")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // 使い回しを検知したので、本人の新しいトークンも無効になっている
        assertThat(activeTokens()).isZero();
        victim.post("/api/auth/refresh").andExpect(status().isUnauthorized());
    }

    @Test
    void ログアウトで無効にしたトークンが届いても他の端末のトークンは無効にしない() throws Exception {
        ApiClient deviceA = new ApiClient(mvc);
        deviceA.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        Cookie loggedOut = deviceA.cookie("REFRESH_TOKEN");
        deviceA.post("/api/auth/logout").andExpect(status().isNoContent());
        ApiClient deviceB = new ApiClient(mvc);
        deviceB.post("/api/auth/login", CORRECT).andExpect(status().isOk());

        ApiClient stale = new ApiClient(mvc);
        stale.setCookie(loggedOut);
        stale.post("/api/auth/refresh").andExpect(status().isUnauthorized());

        deviceB.post("/api/auth/refresh").andExpect(status().isOk()); // 盗まれたとはみなさない
    }

    @Test
    void リフレッシュトークンがない期限切れ不正なら取り直せない() throws Exception {
        new ApiClient(mvc).post("/api/auth/refresh").andExpect(status().isUnauthorized());

        ApiClient forged = new ApiClient(mvc);
        forged.setCookie(new Cookie("REFRESH_TOKEN", "forged-token"));
        forged.post("/api/auth/refresh").andExpect(status().isUnauthorized());

        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second'");
        client.post("/api/auth/refresh").andExpect(status().isUnauthorized());
    }

    @Test
    void ログアウトするとリフレッシュトークンが無効になりCookieも消える() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        Cookie refresh = client.cookie("REFRESH_TOKEN");

        client.post("/api/auth/logout").andExpect(status().isNoContent());

        assertThat(client.cookie("REFRESH_TOKEN")).isNull(); // Cookie を消す指示が返っている
        assertThat(activeTokens()).isZero();
        ApiClient again = new ApiClient(mvc);
        again.setCookie(refresh);
        again.post("/api/auth/refresh").andExpect(status().isUnauthorized());
    }

    @Test
    void ログインしていなくてもログアウトは204を返す() throws Exception {
        new ApiClient(mvc).post("/api/auth/logout").andExpect(status().isNoContent());
    }

    @Test
    void Cookieを使うAPIはXRequestedWithがなければ403を返す() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        client.withoutRequestedWith();
        client.post("/api/auth/refresh")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        client.post("/api/auth/logout").andExpect(status().isForbidden());
        assertThat(activeTokens()).isEqualTo(1); // 何も変わっていない
    }

    @Test
    void ログインしてもセッションを作らない() throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        assertThat(client.cookie("JSESSIONID")).isNull();
        assertThat(client.cookie("SESSION")).isNull();
    }

    @Test
    void 空のメールアドレスとパスワードは入力してくださいを返す() throws Exception {
        new ApiClient(mvc)
                .post("/api/auth/login", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }
}
