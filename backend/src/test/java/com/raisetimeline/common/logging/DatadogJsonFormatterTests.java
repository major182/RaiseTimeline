package com.raisetimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON のログの形と重大度（status）の単体テスト（ログ設計書 2・3 章）。 */
@DisplayName("UT-CO-04 ログの JSON の形と重大度")
class DatadogJsonFormatterTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger("com.raisetimeline.Sample");

    private final DatadogJsonFormatter formatter = new DatadogJsonFormatter(new MockEnvironment()
            .withProperty("spring.application.name", "raisetimeline")
            .withProperty("app.logging.env", "prod")
            .withProperty("app.logging.version", ""));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static LoggingEvent event(Level level, Throwable error, Marker... markers) {
        LoggingEvent event = new LoggingEvent(Logger.FQCN, LOGGER, level, "こんにちは {}", error, new Object[] {"世界"});
        for (Marker marker : markers) {
            event.addMarker(marker);
        }
        return event;
    }

    private JsonNode format(LoggingEvent event) {
        String line = formatter.format(event);
        assertThat(line).endsWith("\n").doesNotContain("\n\n");
        assertThat(line.strip()).doesNotContain("\n"); // 1 件を 1 行で出す（Datadog が行ごとに読む）
        return JSON.readTree(line);
    }

    @Test
    void 基本の属性をDatadogの名前で出し空の値は出さない() {
        JsonNode json = format(event(Level.INFO, null));
        assertThat(json.get("timestamp").asString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(json.get("status").asString()).isEqualTo("info");
        assertThat(json.get("message").asString()).isEqualTo("こんにちは 世界");
        assertThat(json.get("service").asString()).isEqualTo("raisetimeline");
        assertThat(json.get("env").asString()).isEqualTo("prod");
        assertThat(json.has("version")).isFalse(); // 空文字は出さない
        assertThat(json.get("logger").get("name").asString()).isEqualTo("com.raisetimeline.Sample");
        assertThat(json.get("logger").has("thread_name")).isTrue();
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void 設定がなければserviceなどを出さない() {
        DatadogJsonFormatter bare = new DatadogJsonFormatter(new MockEnvironment());
        String line = bare.format(event(Level.INFO, null));
        assertThat(JSON.readTree(line).has("service")).isFalse();
        assertThat(JSON.readTree(line).has("env")).isFalse();
    }

    @ParameterizedTest(name = "{0}＋{1} → {2}")
    @CsvSource({
        "ERROR, ALERT, alert",
        "ERROR, CRITICAL, critical",
        "ERROR, , error",
        "WARN, , warning",
        "INFO, NOTICE, notice",
        "INFO, , info",
        "DEBUG, , debug",
        "INFO, OTHER, info",
    })
    void 印とレベルから重大度を決める(String level, String marker, String expected) {
        Marker[] markers = switch (String.valueOf(marker)) {
            case "ALERT" -> new Marker[] {LogMarkers.ALERT};
            case "CRITICAL" -> new Marker[] {LogMarkers.CRITICAL};
            case "NOTICE" -> new Marker[] {LogMarkers.NOTICE};
            case "OTHER" -> new Marker[] {org.slf4j.MarkerFactory.getMarker("OTHER")};
            default -> new Marker[0];
        };
        assertThat(format(event(Level.toLevel(level), null, markers)).get("status").asString()).isEqualTo(expected);
    }

    @Test
    void 印が複数あれば重いほうを使う() {
        assertThat(DatadogJsonFormatter.status(Level.ERROR, List.of(LogMarkers.NOTICE, LogMarkers.CRITICAL, LogMarkers.ALERT)))
                .isEqualTo("alert");
        assertThat(DatadogJsonFormatter.status(Level.ERROR, List.of(LogMarkers.NOTICE, LogMarkers.CRITICAL)))
                .isEqualTo("critical");
    }

    @Test
    void 例外の種類とメッセージとスタックトレースを出す() {
        JsonNode error = format(event(Level.ERROR, new IllegalStateException("こわれた\n2 行目"))).get("error");
        assertThat(error.get("kind").asString()).isEqualTo("java.lang.IllegalStateException");
        assertThat(error.get("message").asString()).isEqualTo("こわれた\n2 行目");
        assertThat(error.get("stack").asString()).contains("IllegalStateException", "DatadogJsonFormatterTests");
    }

    @Test
    void MDCとkeyvalueを入れ子のオブジェクトにして出す() {
        MDC.put("request_id", "abc");
        MDC.put("usr.id", "7");
        LoggingEvent event = event(Level.INFO, null);
        event.addKeyValuePair(new KeyValuePair("http.status_code", 200));
        event.addKeyValuePair(new KeyValuePair("http.method", "GET"));
        event.addKeyValuePair(new KeyValuePair("evt.ok", true));
        event.addKeyValuePair(new KeyValuePair("evt.reason", null)); // null は出さない
        event.addKeyValuePair(new KeyValuePair("evt.list", List.of(1, 2))); // 数・文字・真偽以外は文字にする
        JsonNode json = format(event);
        assertThat(json.get("request_id").asString()).isEqualTo("abc");
        assertThat(json.get("usr").get("id").asString()).isEqualTo("7");
        assertThat(json.get("http").get("status_code").asInt()).isEqualTo(200);
        assertThat(json.get("http").get("method").asString()).isEqualTo("GET");
        assertThat(json.get("evt").get("ok").asBoolean()).isTrue();
        assertThat(json.get("evt").has("reason")).isFalse();
        assertThat(json.get("evt").get("list").asString()).isEqualTo("[1, 2]");
    }

    @Test
    void 同じ名前の値があれば入れ子のオブジェクトで置き換える() {
        Map<String, Object> json = new java.util.LinkedHashMap<>();
        DatadogJsonFormatter.put(json, "usr", "x");
        DatadogJsonFormatter.put(json, "usr.id", 1);
        assertThat(json).isEqualTo(Map.of("usr", Map.of("id", 1)));
    }
}
