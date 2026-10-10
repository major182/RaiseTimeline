package com.raisetimeline.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Swagger UI（API 設計書 5 章）。開発の設定でだけ出し、本番と同じ既定の設定では出さない。 */
class SwaggerUiTests {

    /** 既定の設定（本番と同じ）。 */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    class DisabledByDefault {

        @Autowired
        private MockMvc mvc;

        @Test
        void APIの説明もSwaggerUIも出さない() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
            // 画面の URL と間違えて index.html を返すこともしない
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }

    /** 開発の設定（application-dev.yaml と同じく有効にする）。 */
    @Nested
    @SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    class EnabledInDev {

        @Autowired
        private MockMvc mvc;

        @Test
        void ログインなしでAPIの説明を返し_アクセストークンの入力欄がある() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("RaiseTimeline API"))
                    .andExpect(jsonPath("$.paths['/api/posts']").exists())
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        }

        @Test
        void SwaggerUIの画面を返す() throws Exception {
            mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("swagger-ui")));
        }
    }
}
