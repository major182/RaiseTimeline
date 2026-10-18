package com.raisetimeline.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.ApiClient;
import com.raisetimeline.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/** 認証（JWT の検証）・エラーの形・セキュリティのヘッダー（API 設計書 2.2、技術選定書 4.7）。 */
@DisplayName("IT-AU-04・IT-CO-04 ログインの確かめ・エラーの形・セキュリティのヘッダー")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityConfigTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder encoder;

    private String token(String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("1")
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void トークンがなければAPIは401をJSONで返す() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/auth/me"));
    }

    /**
     * ログインが要る API は、どれもログインしていなければ 401 を返す（BR-06、API 設計書 2.2）。
     * 一部の API だけで確かめると、新しい API で設定を忘れても気づけないため、API 設計書 4 章の API をすべて並べる。
     * 対象が存在するかを確かめる前に断るので、ID はない値でよい。
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /api/auth/me",
        "PUT, /api/me/password",
        "GET, /api/users/by-username/someone",
        "GET, /api/users/1",
        "PATCH, /api/me/profile",
        "PUT, /api/me/avatar",
        "GET, /api/users/search?q=a",
        "GET, /api/users/recommendations",
        "GET, /api/timeline/following",
        "GET, /api/timeline/all",
        "GET, /api/users/1/posts",
        "POST, /api/posts",
        "GET, /api/posts/1",
        "PATCH, /api/posts/1",
        "DELETE, /api/posts/1",
        "GET, /api/posts/1/comments",
        "GET, /api/comments/1/replies",
        "POST, /api/posts/1/comments",
        "DELETE, /api/comments/1",
        "PUT, /api/posts/1/like",
        "DELETE, /api/posts/1/like",
        "GET, /api/posts/1/likes",
        "PUT, /api/users/1/follow",
        "DELETE, /api/users/1/follow",
        "GET, /api/users/1/following",
        "GET, /api/users/1/followers"
    })
    void ログインが要るAPIはどれもトークンがなければ401を返す(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 期限切れのトークンは401を返す() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("raisetimeline", Instant.now().minusSeconds(120))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 発行者が違うトークンは401を返す() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("someone-else", Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 署名が正しくないトークンは401を返す() throws Exception {
        String valid = token("raisetimeline", Instant.now().plusSeconds(600));
        String tampered = valid.substring(0, valid.length() - 4) + "AAAA"; // 署名の部分を書き換える
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 存在しないURLは404をJSONで返す() throws Exception {
        jdbc.update("DELETE FROM users WHERE username = 'notfound_test'");
        ApiClient client = new ApiClient(mvc);
        client.post(
                        "/api/auth/signup",
                        """
                        {"email":"notfound@example.com","password":"pass1234","passwordConfirmation":"pass1234","username":"notfound_test"}
                        """)
                .andExpect(status().isCreated());
        client.get("/api/no-such-api")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("見つかりません"));
    }

    @Test
    void ヘルスチェックはログインなしで使える() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void すべての応答にXSS対策のヘッダーが付く() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}
