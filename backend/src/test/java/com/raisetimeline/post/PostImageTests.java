package com.raisetimeline.post;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** 投稿の画像（F-PO-02、BR-11・13・20〜22、API 設計書 4.4）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostImageTests {

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

    private static MockMultipartFile image(byte[] data) {
        return TestImages.file("images", "photo.png", "image/png", data);
    }

    /** 保存先のフォルダにある、投稿の画像のファイルの数。 */
    private long storedFiles() throws Exception {
        Path posts = storage.localDir().resolve("posts");
        if (!Files.exists(posts)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(posts)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private int rows(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void 画像を送った順に並べ幅と高さと署名つきURLを返す() throws Exception {
        String json = me.client()
                .multipart(
                        "/api/posts",
                        new MockMultipartFile[] {
                            image(TestImages.png(640, 480)),
                            TestImages.file("images", "a.webp", "image/webp", TestImages.webpLossy(300, 200))
                        },
                        "body",
                        "写真です")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].width").value(640))
                .andExpect(jsonPath("$.images[0].height").value(480))
                .andExpect(jsonPath("$.images[1].width").value(300))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String url = JsonPath.read(json, "$.images[0].url");
        assertThat(url).startsWith("/media/posts/").contains(".png?expires=").contains("&signature=");
        assertThat(JsonPath.<String>read(json, "$.images[1].url")).contains(".webp?");
        List<String> types = jdbc.queryForList("SELECT content_type FROM post_images ORDER BY position", String.class);
        assertThat(types).containsExactly("image/png", "image/webp");
        // URL で画像を読める（ログインなし）
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void 拡張子ではなく中身で形式を決める() throws Exception {
        me.client()
                .multipart(
                        "/api/posts",
                        new MockMultipartFile[] {TestImages.file("images", "photo.png", "image/png", TestImages.gif(8, 8))})
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images[0].url").value(org.hamcrest.Matchers.containsString(".gif?")));
        assertThat(jdbc.queryForObject("SELECT content_type FROM post_images", String.class)).isEqualTo("image/gif");
    }

    @Test
    void 画像があれば本文は空でよい() throws Exception {
        me.client()
                .multipart("/api/posts", new MockMultipartFile[] {image(TestImages.png(10, 10))})
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(""));
    }

    @Test
    void 画像は4枚までで5枚ならIMAGE_TOO_MANYで何も保存しない() throws Exception {
        MockMultipartFile[] four = new MockMultipartFile[4];
        for (int i = 0; i < 4; i++) {
            four[i] = image(TestImages.png(10, 10));
        }
        me.client().multipart("/api/posts", four).andExpect(status().isCreated());

        MockMultipartFile[] five = new MockMultipartFile[5];
        for (int i = 0; i < 5; i++) {
            five[i] = image(TestImages.png(10, 10));
        }
        me.client()
                .multipart("/api/posts", five, "body", "本文")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_MANY"))
                .andExpect(jsonPath("$.detail").value("画像は 4 枚まで添付できます"));
        assertThat(rows("posts")).isEqualTo(1);
        assertThat(rows("post_images")).isEqualTo(4);
    }

    @Test
    void 画像でないファイルは415で1枚でも誤りがあれば何も保存しない() throws Exception {
        long before = storedFiles();
        MockMultipartFile fake = TestImages.file(
                "images", "evil.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        me.client()
                .multipart("/api/posts", new MockMultipartFile[] {image(TestImages.png(10, 10)), fake}, "body", "本文")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("IMAGE_TYPE_INVALID"));
        assertThat(rows("posts")).isZero();
        assertThat(storedFiles()).isEqualTo(before);
    }

    @Test
    void 五MBを超える画像は413() throws Exception {
        byte[] big = new byte[(int) ImageUploads.MAX_BYTES + 1];
        me.client()
                .multipart("/api/posts", new MockMultipartFile[] {image(big)})
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        assertThat(rows("posts")).isZero();
    }

    @Test
    void 画像のある投稿は本文を空に編集できる() throws Exception {
        String json = me.client()
                .multipart("/api/posts", new MockMultipartFile[] {image(TestImages.png(10, 10))}, "body", "本文")
                .andReturn()
                .getResponse()
                .getContentAsString();
        long postId = ((Number) JsonPath.read(json, "$.id")).longValue();
        me.client()
                .patch("/api/posts/" + postId, "{\"body\":\"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.images.length()").value(1)); // 画像は変わらない（BR-15）
    }

    @Test
    void 投稿を消すと画像の行とファイルも消える() throws Exception {
        String json = me.client()
                .multipart("/api/posts", new MockMultipartFile[] {image(TestImages.png(10, 10))})
                .andReturn()
                .getResponse()
                .getContentAsString();
        long postId = ((Number) JsonPath.read(json, "$.id")).longValue();
        String key = jdbc.queryForObject("SELECT storage_key FROM post_images", String.class);
        assertThat(storage.localDir().resolve(key)).exists();

        me.client().delete("/api/posts/" + postId).andExpect(status().isNoContent());
        assertThat(rows("post_images")).isZero();
        assertThat(storage.localDir().resolve(key)).doesNotExist();
    }

    @Test
    void タイムラインの投稿カードにも画像が付く() throws Exception {
        me.client().multipart("/api/posts", new MockMultipartFile[] {image(TestImages.png(10, 20))});
        me.client()
                .get("/api/timeline/all")
                .andExpect(jsonPath("$.items[0].images[0].width").value(10))
                .andExpect(jsonPath("$.items[0].images[0].height").value(20));
    }
}
