package com.raisetimeline.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI に出す API の説明（API 設計書 5 章）。Swagger UI は開発のときだけ有効にする（application-dev.yaml）。
 *
 * <p>ログインの要る API を試すときは、ログインの応答の accessToken を「Authorize」に入れる（API 設計書 2.2）。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("RaiseTimeline API").description("API 設計書（docs/05_api-design.md）の API"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                // すべての API にアクセストークンを付けて送る（ログインの API には付けても影響しない）
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
