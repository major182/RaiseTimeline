package com.raisetimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** 利用者登録と、ログインしている利用者の取得（認証の段階 3）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupTests {

    private static final String VALID =
            """
            {"email":"me@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"raise_me"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient client;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM users");
        client = new ApiClient(mvc);
    }

    @Test
    void 登録するとログインした状態になりmeで自分を取れる() throws Exception {
        client.post("/api/auth/signup", VALID)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/auth/me"))
                .andExpect(jsonPath("$.username").value("raise_me"))
                .andExpect(jsonPath("$.displayName").value("raise_me")) // 表示名はユーザー名と同じ（BR-01）
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.avatarUrl").isEmpty());
        assertThat(client.cookie("SESSION")).isNotNull(); // Cookie の属性は SessionCookieTests で確かめる

        client.get("/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.username").value("raise_me"));
    }

    @Test
    void セッションはDBに保存されログインした利用者のIDが入る() throws Exception {
        client.post("/api/auth/signup", VALID).andExpect(status().isCreated());
        Long userId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        var principals = jdbc.queryForList("SELECT principal_name FROM spring_session", String.class);
        assertThat(principals).containsExactly(String.valueOf(userId));
    }

    @Test
    void パスワードはハッシュにして保存し元の値は保存しない() throws Exception {
        client.post("/api/auth/signup", VALID).andExpect(status().isCreated());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).isNotEqualTo("pass1234").startsWith("$2"); // BCrypt のハッシュは $2 で始まる
    }

    @Test
    void 入力の誤りは入力欄ごとに理由を返す() throws Exception {
        client.post(
                        "/api/auth/signup",
                        """
                        {"email":"not-mail","password":"short","passwordConfirmation":"other","username":"a"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("email", "password", "passwordConfirmation", "username")))
                .andExpect(jsonPath("$.errors[*].code")
                        .value(containsInAnyOrder(
                                "EMAIL_INVALID", "PASSWORD_WEAK", "PASSWORD_MISMATCH", "USERNAME_INVALID")))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message")
                        .value("8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください"));
    }

    @Test
    void 空の項目は入力してくださいを返す() throws Exception {
        client.post("/api/auth/signup", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[*].code")
                        .value(containsInAnyOrder("REQUIRED", "REQUIRED", "REQUIRED", "REQUIRED")));
    }

    @Test
    void 英字だけ数字だけのパスワードは使えない() throws Exception {
        for (String password : new String[] {"password", "12345678"}) {
            client.post(
                            "/api/auth/signup",
                            """
                            {"email":"me@example.com","password":"%s","passwordConfirmation":"%s","username":"raise_me"}
                            """
                                    .formatted(password, password))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_WEAK"));
        }
    }

    @Test
    void メールアドレスとユーザー名の重複は大文字小文字を区別せず409を返す() throws Exception {
        client.post("/api/auth/signup", VALID).andExpect(status().isCreated());
        new ApiClient(mvc)
                .post(
                        "/api/auth/signup",
                        """
                        {"email":"ME@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"RAISE_ME"}
                        """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[*].code").value(containsInAnyOrder("EMAIL_TAKEN", "USERNAME_TAKEN")));
    }

    @Test
    void JSONが壊れていれば400を返す() throws Exception {
        client.post("/api/auth/signup", "{broken")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
