package com.raisetimeline.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 仕様書（OpenAPI）を docs/api/openapi.yaml に書き出す（API 設計書 5.1）。
 * ./gradlew test のたびに書き直す。CI は、書き出したものがコミットされたものと同じかを確かめる（更新忘れを止める）。
 *
 * <p>アプリを起動して取りに行く方法と違い、DB や環境変数を用意しなくてよい（他のテストと同じ Testcontainers を使う）。
 */
@DisplayName("IT-CO-03 API 仕様書の書き出し")
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSpecExportTests {

    /** テストは backend で動くので、一つ上がリポジトリの根。 */
    private static final Path OUTPUT = Path.of("..", "docs", "api", "openapi.yaml");

    @Autowired
    private MockMvc mvc;

    @Test
    void API仕様書を書き出す() throws Exception {
        String yaml = mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, yaml.endsWith("\n") ? yaml : yaml + "\n", StandardCharsets.UTF_8);
    }
}
