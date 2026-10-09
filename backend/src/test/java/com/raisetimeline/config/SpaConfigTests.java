package com.raisetimeline.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 画面の配信（API 設計書 5 章）。画面の URL を直接開いたら index.html を返し、API などは 404 のままにする。
 * テスト用の画面のファイルは src/test/resources/static に置いてある。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaConfigTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 画面のURLを直接開くとログインなしでindexを返す() throws Exception {
        for (String path : new String[] {"/login", "/users/raise_user", "/users/raise_user/followers", "/posts/12"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("spa-test-index")));
        }
    }

    @Test
    void トップはindexを返す() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void 画面のファイルはそのまま返す() throws Exception {
        mvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spa-test-asset")));
    }

    @Test
    void ないファイルはindexではなく404() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    void ないAPIはindexではなくJSONの404() throws Exception {
        jdbc.update("DELETE FROM users");
        TestUser me = TestUsers.signup(mvc, "me_user");
        me.client()
                .get("/api/no-such-api")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/actuator/no-such"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/media/no-such.png")).andExpect(status().isNotFound());
    }
}
