package com.raisetimeline.auth;

import static org.hamcrest.Matchers.containsInAnyOrder;
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

/** パスワードの変更（認証の段階 5）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChangePasswordTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        client = new ApiClient(mvc);
        client.post(
                        "/api/auth/signup",
                        """
                        {"email":"me@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"raise_me"}
                        """)
                .andExpect(status().isCreated());
    }

    @Test
    void 変更すると新しいパスワードでだけログインできる() throws Exception {
        client.put(
                        "/api/me/password",
                        """
                        {"currentPassword":"pass1234","newPassword":"newpass5678","newPasswordConfirmation":"newpass5678"}
                        """)
                .andExpect(status().isNoContent());

        new ApiClient(mvc)
                .post("/api/auth/login", """
                        {"email":"me@example.com","password":"pass1234"}
                        """)
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        new ApiClient(mvc)
                .post("/api/auth/login", """
                        {"email":"me@example.com","password":"newpass5678"}
                        """)
                .andExpect(status().isOk());
    }

    @Test
    void 変更すると他の端末は取り直せなくなり今の端末は続けて使える() throws Exception {
        ApiClient otherDevice = new ApiClient(mvc);
        otherDevice
                .post("/api/auth/login", """
                        {"email":"me@example.com","password":"pass1234"}
                        """)
                .andExpect(status().isOk());

        client.put(
                        "/api/me/password",
                        """
                        {"currentPassword":"pass1234","newPassword":"newpass5678","newPasswordConfirmation":"newpass5678"}
                        """)
                .andExpect(status().isNoContent());

        otherDevice.post("/api/auth/refresh").andExpect(status().isUnauthorized());
        client.post("/api/auth/refresh").andExpect(status().isOk());
    }

    @Test
    void 今のパスワードが違えばその入力欄の誤りを返し変更しない() throws Exception {
        client.put(
                        "/api/me/password",
                        """
                        {"currentPassword":"wrong123","newPassword":"newpass5678","newPasswordConfirmation":"newpass5678"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"))
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("現在のパスワードが違います"));

        new ApiClient(mvc)
                .post("/api/auth/login", """
                        {"email":"me@example.com","password":"pass1234"}
                        """)
                .andExpect(status().isOk());
    }

    @Test
    void 新しいパスワードの条件と確認を確かめる() throws Exception {
        client.put(
                        "/api/me/password",
                        """
                        {"currentPassword":"pass1234","newPassword":"short","newPasswordConfirmation":"other"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].code").value(containsInAnyOrder("PASSWORD_WEAK", "PASSWORD_MISMATCH")))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("newPassword", "newPasswordConfirmation")));
    }

    @Test
    void ログインしていなければ401を返す() throws Exception {
        new ApiClient(mvc)
                .put(
                        "/api/me/password",
                        """
                        {"currentPassword":"pass1234","newPassword":"newpass5678","newPasswordConfirmation":"newpass5678"}
                        """)
                .andExpect(status().isUnauthorized());
    }
}
