package com.raisetimeline.config;

import com.raisetimeline.common.error.ApiErrors;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.error.ProblemResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 仕様書（OpenAPI）の共通の部分（API 設計書 5.1）。
 * 仕様書はテスト（OpenApiSpecExportTests）が docs/api/openapi.yaml に書き出す。Swagger UI は開発のときだけ有効にする（application-dev.yaml）。
 *
 * <p>ログインの要る API を試すときは、ログインの応答の accessToken を「Authorize」に入れる（API 設計書 2.2）。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String PROBLEM_REF = "#/components/schemas/Problem";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RaiseTimeline API")
                        .version("v1")
                        .description("""
                                タイムライン型 SNS（RaiseTimeline）の API。
                                方針・認証・エラー・一覧の考え方は API 設計書（docs/05_api-design.md）を参照。
                                エラーはすべて ProblemDetail（RFC 9457）に code（エラーの種類）を加えて返す。"""))
                // 書き出すたびに中身が変わらないよう、URL は相対にする（画面と API は同じオリジン。技術選定書 4.2）
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                // ログインの要らない API は @SecurityRequirements で外す
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * エラーの応答を足す。@ApiErrors に書いたものに加え、ログインの要る API には 401、すべての API に 500 を足す。
     * 同じ状態コードに複数の code があれば、1 つの応答の説明にまとめる。
     */
    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            List<ErrorCode> codes = new ArrayList<>();
            ApiErrors errors = handlerMethod.getMethodAnnotation(ApiErrors.class);
            if (errors != null) {
                codes.addAll(Arrays.asList(errors.value()));
            }
            boolean isPublic = operation.getSecurity() != null && operation.getSecurity().isEmpty();
            if (!isPublic && !codes.contains(ErrorCode.UNAUTHENTICATED)) {
                codes.add(ErrorCode.UNAUTHENTICATED);
            }
            codes.add(ErrorCode.INTERNAL_ERROR);

            Map<Integer, List<ErrorCode>> byStatus = codes.stream()
                    .collect(Collectors.groupingBy(c -> c.status().value(), LinkedHashMap::new, Collectors.toList()));
            byStatus.forEach((status, list) ->
                    operation.getResponses().addApiResponse(String.valueOf(status), errorResponse(list)));
            return operation;
        };
    }

    /**
     * 仕様書の全体を整える。
     * エラーの本文の形（Problem）を足し、応答の形はすべての項目を必ず返すものとする（値がない項目も省略せず null。API 設計書 2.1）。
     */
    @Bean
    OpenApiCustomizer schemas() {
        return openApi -> {
            ModelConverters.getInstance(true)
                    .readAll(ProblemResponse.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                boolean isRequest = name.endsWith("Request");
                if (!isRequest && schema.getProperties() != null && schema.getRequired() == null) {
                    List<String> names = new ArrayList<>(schema.getProperties().keySet());
                    // Problem の errors だけは、入力の誤りがあるときにしか付かない
                    if ("Problem".equals(name)) {
                        names.remove("errors");
                    }
                    schema.setRequired(names);
                }
            });
        };
    }

    private static ApiResponse errorResponse(List<ErrorCode> codes) {
        String description = codes.stream()
                .map(c -> "`" + c.name() + "`：" + c.message())
                .collect(Collectors.joining("<br>"));
        return new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType(
                                org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
    }
}
