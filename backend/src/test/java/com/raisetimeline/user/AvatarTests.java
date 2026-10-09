package com.raisetimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.config.StorageProperties;
import com.raisetimeline.image.ImageUploads;
import com.raisetimeline.image.TestImages;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** アイコンの変更（F-US-02、BR-24、API 設計書 4.2）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AvatarTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StorageProperties storage;

    private TestUser me;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
    }

    /** PUT の multipart で送る（HTML のフォームは PUT を送れないが、画面は fetch で送る）。 */
    private ResultActions upload(MockMultipartFile... files) throws Exception {
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/me/avatar");
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        return mvc.perform(builder.header("Authorization", "Bearer " + me.client().accessToken()));
    }

    private String avatarKey() {
        return jdbc.queryForObject("SELECT avatar_key FROM users WHERE id = ?", String.class, me.id());
    }

    @Test
    void アイコンを変えるとプロフィールとログイン中の利用者に署名つきURLが入る() throws Exception {
        String json = upload(TestImages.pngFile("file", 64, 64))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.id()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String url = JsonPath.read(json, "$.avatarUrl");
        assertThat(url).startsWith("/media/avatars/").contains("signature=");
        assertThat(avatarKey()).startsWith("avatars/").endsWith(".png");
        me.client().get("/api/auth/me").andExpect(jsonPath("$.avatarUrl").isNotEmpty());
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void 一覧の利用者の要約にもアイコンが入る() throws Exception {
        upload(TestImages.pngFile("file", 8, 8));
        long other = TestUsers.insert(jdbc, "other_one");
        TestUsers.follow(jdbc, me.id(), other);
        me.client()
                .get("/api/users/" + other + "/followers")
                .andExpect(jsonPath("$.items[0].avatarUrl").value(org.hamcrest.Matchers.startsWith("/media/avatars/")));
    }

    @Test
    void 変えたら古いファイルを消す() throws Exception {
        upload(TestImages.pngFile("file", 8, 8));
        String old = avatarKey();
        upload(TestImages.file("file", "new.gif", "image/gif", TestImages.gif(8, 8))).andExpect(status().isOk());
        assertThat(avatarKey()).isNotEqualTo(old).endsWith(".gif");
        assertThat(storage.localDir().resolve(old)).doesNotExist();
        assertThat(storage.localDir().resolve(avatarKey())).exists();
    }

    @Test
    void 画像でなければ415でアイコンは変わらない() throws Exception {
        upload(TestImages.pngFile("file", 8, 8));
        String before = avatarKey();
        upload(TestImages.file("file", "a.png", "image/png", "not image".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("IMAGE_TYPE_INVALID"));
        assertThat(avatarKey()).isEqualTo(before);
        assertThat(storage.localDir().resolve(before)).exists();
    }

    @Test
    void 五MBを超えると413() throws Exception {
        upload(TestImages.file("file", "big.png", "image/png", new byte[(int) ImageUploads.MAX_BYTES + 1]))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        assertThat(avatarKey()).isNull();
    }

    @Test
    void ファイルがなければ400() throws Exception {
        upload().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void 未設定ならavatarUrlはnull() throws Exception {
        me.client().get("/api/users/" + me.id()).andExpect(jsonPath("$.avatarUrl").isEmpty());
    }
}
