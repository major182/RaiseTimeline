package com.raisetimeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 開発用のテストデータ（db/dev-data。DB 設計書 7 章）。開発のときと同じく Flyway に読み込ませ、中身を確かめる。
 * 設定が違うので、ほかのテストとは別の DB（コンテナ）で動く。テストの中でデータを消さないこと。
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev-data")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DevDataTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient login(String email) throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"pass1234\"}".formatted(email))
                .andExpect(status().isOk());
        return client;
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void 三人ともpass1234でログインできる() throws Exception {
        for (String email : new String[] {"sato@example.com", "suzuki@example.com", "tanaka@example.com"}) {
            login(email).get("/api/auth/me").andExpect(jsonPath("$.email").value(email));
        }
    }

    @Test
    void 全体タブは20件を超えて続きを読める() throws Exception {
        assertThat(count("SELECT count(*) FROM posts")).isGreaterThan(20);
        login("suzuki@example.com")
                .get("/api/timeline/all")
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());
    }

    @Test
    void 佐藤のフォロー中タブにいいね経由の投稿とハイライトが出る() throws Exception {
        // 最後に開いた時刻を 7 時間前に戻す（ほかのテストで開くと、今の時刻になるため）
        jdbc.update("UPDATE users SET last_timeline_viewed_at = now() - interval '7 hours' WHERE username = 'sato_hana'");
        login("sato@example.com")
                .get("/api/timeline/following")
                .andExpect(jsonPath("$.highlights.length()").value(2))
                .andExpect(jsonPath("$.highlights[0].author.username").value("suzuki_ken"))
                .andExpect(jsonPath("$.items[?(@.likedVia != null)].likedVia.displayName").value("鈴木 健"))
                .andExpect(jsonPath("$.items[?(@.likedVia != null)].author.username").value("tanaka_yu"));
    }

    @Test
    void もう一度実行しても二重に入らない() throws Exception {
        int users = count("SELECT count(*) FROM users");
        int posts = count("SELECT count(*) FROM posts");
        String sql = new ClassPathResource("db/dev-data/R__dev_data.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
        assertThat(count("SELECT count(*) FROM users")).isEqualTo(users);
        assertThat(count("SELECT count(*) FROM posts")).isEqualTo(posts);
    }
}
