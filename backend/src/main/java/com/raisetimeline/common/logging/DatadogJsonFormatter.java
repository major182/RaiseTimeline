package com.raisetimeline.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * 1 件のログを 1 行の JSON にする（ログ設計書 2 章）。属性の名前は Datadog の予約属性・標準属性に合わせる。
 *
 * <p>名前に「.」を含む属性（{@code logger.name}・{@code usr.id} など）は入れ子のオブジェクトにする。
 * MDC と SLF4J の key-value（{@code LOG.atInfo().addKeyValue(...)}）も同じように出す。
 * {@code logging.structured.format.console} にこのクラスの名前を書くと、Spring Boot が作って使う。
 */
public class DatadogJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final JsonWriter<Map<String, Object>> WRITER = JsonWriter.<Map<String, Object>>standard().withNewLineAtEnd();

    private final @Nullable String service;
    private final @Nullable String env;
    private final @Nullable String version;

    public DatadogJsonFormatter(Environment environment) {
        this.service = environment.getProperty("spring.application.name");
        this.env = environment.getProperty("app.logging.env");
        this.version = environment.getProperty("app.logging.version");
    }

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> json = new LinkedHashMap<>();
        put(json, "timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        put(json, "status", status(event.getLevel(), event.getMarkerList()));
        put(json, "message", event.getFormattedMessage());
        put(json, "service", service);
        put(json, "env", env);
        put(json, "version", version);
        put(json, "logger.name", event.getLoggerName());
        put(json, "logger.thread_name", event.getThreadName());
        event.getMDCPropertyMap().forEach((key, value) -> put(json, key, value));
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            pairs.forEach(pair -> put(json, pair.key, pair.value));
        }
        IThrowableProxy error = event.getThrowableProxy();
        if (error != null) {
            put(json, "error.kind", error.getClassName());
            put(json, "error.message", error.getMessage());
            put(json, "error.stack", ThrowableProxyUtil.asString(error));
        }
        return WRITER.writeToString(json);
    }

    /**
     * Datadog の {@code status}。印（{@link LogMarkers}）があればそれを、なければ SLF4J のレベルを使う。
     * 印が複数あるときは、重いほうを使う。
     */
    static String status(Level level, @Nullable List<Marker> markers) {
        if (markers != null) {
            if (markers.contains(LogMarkers.ALERT)) {
                return "alert";
            }
            if (markers.contains(LogMarkers.CRITICAL)) {
                return "critical";
            }
            if (markers.contains(LogMarkers.NOTICE)) {
                return "notice";
            }
        }
        return switch (level.toInt()) {
            case Level.ERROR_INT -> "error";
            case Level.WARN_INT -> "warning";
            case Level.INFO_INT -> "info";
            default -> "debug"; // DEBUG・TRACE は設定で出さない（logging.level.root=INFO）
        };
    }

    /** 「.」で区切った名前を入れ子にして入れる。値が null・空なら入れない。 */
    @SuppressWarnings("unchecked")
    static void put(Map<String, Object> json, String name, @Nullable Object value) {
        if (value == null || (value instanceof String text && text.isEmpty())) {
            return;
        }
        Map<String, Object> target = json;
        String[] parts = name.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = target.get(parts[i]);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                target.put(parts[i], child);
            }
            target = (Map<String, Object>) child;
        }
        target.put(parts[parts.length - 1], isJsonValue(value) ? value : value.toString());
    }

    private static boolean isJsonValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
