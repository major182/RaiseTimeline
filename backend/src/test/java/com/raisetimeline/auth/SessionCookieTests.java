package com.raisetimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ログインの Cookie の属性（NF-SE-02）。
 * MockMvc では Cookie の属性が再現されないため、実際にサーバーを起動して HTTP で確かめる。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookieTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void ログインのCookieはHttpOnlyとSecureとSameSiteLaxが付く() throws Exception {
        jdbc.update("DELETE FROM users WHERE username = 'cookie_test'");

        HttpResponse<Void> csrf = http.send(
                HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.discarding());
        String token = csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0].split("=", 2)[1];

        HttpResponse<String> signup = http.send(
                HttpRequest.newBuilder(uri("/api/auth/signup"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + token)
                        .header("X-XSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                """
                                {"email":"cookie@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"cookie_test"}
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(signup.statusCode()).isEqualTo(201);
        String session = signup.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("SESSION="))
                .findFirst()
                .orElseThrow();
        assertThat(session).contains("HttpOnly", "Secure", "SameSite=Lax");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
