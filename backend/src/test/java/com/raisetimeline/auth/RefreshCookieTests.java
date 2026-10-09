package com.raisetimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * リフレッシュトークンの Cookie の属性（NF-SE-02）。
 * MockMvc では Cookie の属性が再現されないため、実際にサーバーを起動して HTTP で確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RefreshCookieTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void リフレッシュトークンのCookieはHttpOnlyとSecureとSameSiteStrictで認証のAPIにだけ送られる() throws Exception {
        jdbc.update("DELETE FROM users WHERE username = 'cookie_test'");

        HttpResponse<String> signup = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/signup"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                """
                                {"email":"cookie@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"cookie_test"}
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(signup.statusCode()).isEqualTo(201);
        String cookie = signup.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("REFRESH_TOKEN="))
                .findFirst()
                .orElseThrow();
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        // サーバーにログインの状態を持たないので、セッションの Cookie は返さない
        assertThat(signup.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID="));
    }
}
