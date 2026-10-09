package com.raisetimeline.image;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 開発環境の画像の配信（GET /media/{key}。API 設計書 4.8）。ログインではなく、URL の署名と期限で守る。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MediaControllerTests {

    private static final String KEY = "avatars/123e4567-e89b-12d3-a456-426614174000.png";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ImageStorage storage;

    private byte[] png;

    @BeforeEach
    void setUp() {
        png = TestImages.png(2, 2);
        storage.put(KEY, png, "image/png");
    }

    @Test
    void 署名つきURLならログインなしで画像を返す() throws Exception {
        mvc.perform(get(storage.url(KEY)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")));
    }

    @Test
    void 署名がなければ404() throws Exception {
        mvc.perform(get("/media/" + KEY)).andExpect(status().isNotFound());
    }

    @Test
    void 署名が違えば404() throws Exception {
        String url = storage.url(KEY).replaceAll("signature=[^&]+", "signature=forged");
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void 期限が切れていれば404() throws Exception {
        String url = storage.url(KEY).replaceAll("expires=\\d+", "expires=1");
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void 期限に極端な値や数でない値を入れても500にしない() throws Exception {
        String base = storage.url(KEY);
        mvc.perform(get(base.replaceAll("expires=\\d+", "expires=" + Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(get(base.replaceAll("expires=\\d+", "expires=" + Long.MIN_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(get(base.replaceAll("expires=\\d+", "expires=abc"))).andExpect(status().is4xxClientError());
    }

    @Test
    void 消したファイルは404() throws Exception {
        String url = storage.url(KEY);
        storage.delete(KEY);
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }
}
