package com.raisetimeline.user;

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

/** プロフィールの編集（F-US-02、BR-02・03・25、API 設計書 4.2）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UpdateProfileTests {

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

    private void patchExpectFieldError(String json, String field, String code) throws Exception {
        me.client()
                .patch("/api/me/profile", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    @Test
    void 全部の項目を変えるとプロフィールを返す() throws Exception {
        me.client()
                .patch("/api/me/profile", """
                        {"displayName":"レイズ","username":"new_name","bio":"よろしく\\nお願いします"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("レイズ"))
                .andExpect(jsonPath("$.username").value("new_name"))
                .andExpect(jsonPath("$.bio").value("よろしく\nお願いします"))
                .andExpect(jsonPath("$.isMe").value(true))
                .andExpect(jsonPath("$.followingCount").value(0));
        // 新しいユーザー名で開ける。古いユーザー名では見つからない
        me.client().get("/api/users/by-username/new_name").andExpect(status().isOk());
        me.client().get("/api/users/by-username/me_user").andExpect(status().isNotFound());
    }

    @Test
    void 送らなかった項目は変えない() throws Exception {
        me.client().patch("/api/me/profile", "{\"bio\":\"自己紹介\"}").andExpect(status().isOk());
        me.client()
                .patch("/api/me/profile", "{\"displayName\":\"表示名\"}")
                .andExpect(jsonPath("$.displayName").value("表示名"))
                .andExpect(jsonPath("$.username").value("me_user"))
                .andExpect(jsonPath("$.bio").value("自己紹介"));
    }

    @Test
    void 自己紹介は空にできる() throws Exception {
        me.client().patch("/api/me/profile", "{\"bio\":\"自己紹介\"}");
        me.client().patch("/api/me/profile", "{\"bio\":\"\"}").andExpect(jsonPath("$.bio").value(""));
    }

    @Test
    void 表示名は1から50文字で絵文字は1文字に数える() throws Exception {
        String fifty = "😀".repeat(50);
        me.client()
                .patch("/api/me/profile", "{\"displayName\":\"" + fifty + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(fifty));
        patchExpectFieldError("{\"displayName\":\"" + "😀".repeat(51) + "\"}", "displayName", "DISPLAY_NAME_LENGTH");
        patchExpectFieldError("{\"displayName\":\"\"}", "displayName", "DISPLAY_NAME_LENGTH");
    }

    @Test
    void 自己紹介は160文字まで() throws Exception {
        me.client().patch("/api/me/profile", "{\"bio\":\"" + "あ".repeat(160) + "\"}").andExpect(status().isOk());
        patchExpectFieldError("{\"bio\":\"" + "あ".repeat(161) + "\"}", "bio", "BIO_TOO_LONG");
    }

    @Test
    void ユーザー名の形式が違えば400() throws Exception {
        patchExpectFieldError("{\"username\":\"abc\"}", "username", "USERNAME_INVALID"); // 3 文字
        patchExpectFieldError("{\"username\":\"has-hyphen\"}", "username", "USERNAME_INVALID");
        patchExpectFieldError("{\"username\":\"" + "a".repeat(16) + "\"}", "username", "USERNAME_INVALID");
    }

    @Test
    void ほかの人のユーザー名は大文字小文字が違っても使えず409() throws Exception {
        TestUsers.insert(jdbc, "taken_name");
        me.client()
                .patch("/api/me/profile", "{\"username\":\"TAKEN_NAME\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(jsonPath("$.errors[0].code").value("USERNAME_TAKEN"));
    }

    @Test
    void 自分の今のユーザー名は大文字小文字だけ変えられる() throws Exception {
        me.client()
                .patch("/api/me/profile", "{\"username\":\"ME_USER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ME_USER"));
    }

    @Test
    void 何も送らなければ何も変えない() throws Exception {
        me.client()
                .patch("/api/me/profile", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("me_user"))
                .andExpect(jsonPath("$.displayName").value("me_user"));
    }

    @Test
    void 変えた表示名はログインしている利用者の取得にも反映される() throws Exception {
        me.client().patch("/api/me/profile", "{\"displayName\":\"レイズ\"}");
        me.client().get("/api/auth/me").andExpect(jsonPath("$.displayName").value("レイズ"));
    }
}
