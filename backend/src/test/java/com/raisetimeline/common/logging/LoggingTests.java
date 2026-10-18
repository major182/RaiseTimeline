package com.raisetimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.ApiClient;
import com.raisetimeline.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 実際に API を呼び、標準出力に出た JSON のログを確かめる（ログ設計書 4・5・6 章）。
 * 重大度・リクエストの ID・利用者の ID と、秘密や個人情報が出ないことを見る。
 */
@DisplayName("IT-CO-07 構造化ログの出力")
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LoggingTests.Boom.class})
@ExtendWith(OutputCaptureExtension.class)
class LoggingTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EMAIL = "log-me@example.com";
    private static final String PASSWORD = "pass1234";
    private static final String CORRECT = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";

    /** 想定外の誤り（500）を起こすためだけの API。 */
    @RestController
    static class Boom {
        @GetMapping("/api/test/boom")
        String boom() {
            throw new IllegalStateException("テストのための誤り");
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        new ApiClient(mvc)
                .post(
                        "/api/auth/signup",
                        "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD
                                + "\",\"passwordConfirmation\":\"" + PASSWORD + "\",\"username\":\"log_me\"}")
                .andExpect(status().isCreated());
        userId = jdbc.queryForObject("SELECT id FROM users", Long.class);
    }

    /** 出力のうち JSON の行だけを読む。 */
    private static List<JsonNode> logs(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.startsWith("{")).map(JSON::readTree).toList();
    }

    private static List<JsonNode> withStatus(CapturedOutput output, String status) {
        return logs(output).stream().filter(log -> log.get("status").asString().equals(status)).toList();
    }

    @Test
    void ログの行はすべてJSONで秘密と個人情報を含まない(CapturedOutput output) throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        client.post("/api/auth/login", "{\"email\":\"" + EMAIL + "\",\"password\":\"wrong123\"}");
        client.post("/api/auth/refresh").andExpect(status().isOk());

        assertThat(logs(output)).isNotEmpty();
        String out = output.getOut();
        assertThat(out).doesNotContain(EMAIL, PASSWORD, "wrong123", client.accessToken());
        assertThat(out).doesNotContain(client.cookie("REFRESH_TOKEN").getValue());
    }

    @Test
    void ログインの成功はinfo失敗はnoticeで利用者のIDと理由を出す(CapturedOutput output) throws Exception {
        new ApiClient(mvc).post("/api/auth/login", CORRECT).andExpect(status().isOk());
        new ApiClient(mvc).post("/api/auth/login", "{\"email\":\"" + EMAIL + "\",\"password\":\"wrong123\"}");
        new ApiClient(mvc).post("/api/auth/login", "{\"email\":\"nobody@example.com\",\"password\":\"wrong123\"}");

        JsonNode success = logs(output).stream()
                .filter(log -> log.path("evt").path("outcome").asString().equals("success"))
                .findFirst()
                .orElseThrow();
        assertThat(success.get("status").asString()).isEqualTo("info");
        assertThat(success.get("usr").get("id").asLong()).isEqualTo(userId);

        List<JsonNode> failures = withStatus(output, "notice").stream()
                .filter(log -> log.path("evt").path("name").asString().equals("authentication"))
                .toList();
        assertThat(failures).extracting(log -> log.get("evt").get("reason").asString())
                .containsExactly("wrong_password", "unknown_email");
        assertThat(failures.getFirst().get("usr").get("id").asLong()).isEqualTo(userId);
        assertThat(failures.get(1).has("usr")).isFalse(); // 登録のないメールアドレスは利用者が分からない
    }

    @Test
    void リフレッシュトークンの使い回しはalertで出す(CapturedOutput output) throws Exception {
        ApiClient victim = new ApiClient(mvc);
        victim.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        Cookie stolen = victim.cookie("REFRESH_TOKEN");
        victim.post("/api/auth/refresh").andExpect(status().isOk());
        ApiClient attacker = new ApiClient(mvc);
        attacker.setCookie(stolen);
        attacker.post("/api/auth/refresh").andExpect(status().isUnauthorized());

        List<JsonNode> alerts = withStatus(output, "alert");
        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().get("evt").get("name").asString()).isEqualTo("refresh_token_reuse");
        assertThat(alerts.getFirst().get("usr").get("id").asLong()).isEqualTo(userId);
        assertThat(alerts.getFirst().has("request_id")).isTrue();
    }

    @Test
    void 想定外の誤りはerrorでリクエストのIDと利用者のIDと例外を付けて出す(CapturedOutput output) throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        String requestId = client.get("/api/test/boom")
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        JsonNode error = withStatus(output, "error").getFirst();
        assertThat(error.get("request_id").asString()).isEqualTo(requestId);
        assertThat(error.get("usr").get("id").asString()).isEqualTo(String.valueOf(userId));
        assertThat(error.get("error").get("kind").asString()).isEqualTo("java.lang.IllegalStateException");

        // 同じリクエストのアクセスログにも、同じ ID と 500 が付く
        JsonNode access = logs(output).stream()
                .filter(log -> log.path("http").path("url_details").path("path").asString().equals("/api/test/boom"))
                .findFirst()
                .orElseThrow();
        assertThat(access.get("status").asString()).isEqualTo("info");
        assertThat(access.get("request_id").asString()).isEqualTo(requestId);
        assertThat(access.get("http").get("status_code").asInt()).isEqualTo(500);
        assertThat(access.get("usr").get("id").asString()).isEqualTo(String.valueOf(userId));
    }

    @Test
    void パスワードの変更はnoticeで出す(CapturedOutput output) throws Exception {
        ApiClient client = new ApiClient(mvc);
        client.post("/api/auth/login", CORRECT).andExpect(status().isOk());
        client.put(
                        "/api/me/password",
                        "{\"currentPassword\":\"pass1234\",\"newPassword\":\"pass5678\",\"newPasswordConfirmation\":\"pass5678\"}")
                .andExpect(status().isNoContent());

        assertThat(withStatus(output, "notice"))
                .anySatisfy(log -> assertThat(log.get("evt").get("name").asString()).isEqualTo("password_change"));
        assertThat(output.getOut()).doesNotContain("pass5678");
    }
}
