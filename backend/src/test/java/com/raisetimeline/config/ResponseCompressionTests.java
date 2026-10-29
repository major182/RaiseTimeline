package com.raisetimeline.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * 応答の圧縮（性能テスト設計書 8 章、#90）。圧縮はサーバー（Tomcat）が行い MockMvc では再現されないため、
 * 実際にサーバーを起動して HTTP で確かめる。テスト用の画面のファイルは src/test/resources/static に置いてある。
 */
@DisplayName("IT-CO-08 応答の圧縮")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ResponseCompressionTests {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<byte[]> get(String path, String acceptEncoding) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (acceptEncoding != null) {
            request.header("Accept-Encoding", acceptEncoding);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void ブラウザがgzipに対応していれば1KB以上のJSを圧縮して送る() throws Exception {
        HttpResponse<byte[]> response = get("/assets/large.js", "gzip");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).hasValue("gzip");
    }

    @Test
    void ブラウザがgzipに対応していなければ圧縮しない() throws Exception {
        HttpResponse<byte[]> response = get("/assets/large.js", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(new String(response.body())).contains("compression-test-asset");
    }

    @Test
    void 小さい応答は圧縮しない() throws Exception {
        // テスト用の app.js は、圧縮の対象の下限（1KB）より小さい
        HttpResponse<byte[]> response = get("/assets/app.js", "gzip");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
    }
}
