package com.raisetimeline.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエストごとの ID を決め、終わったらアクセスログを 1 行出す（ログ設計書 4 章）。
 *
 * <p>ID は {@code X-Request-Id} ヘッダーがあればそれを、なければ新しく作り、MDC（{@code request_id}）に入れて
 * 応答のヘッダーにも返す。このリクエストの間に出したログには、すべてこの ID が付く。
 * 認証より前に動かすため、いちばん先に並べる。ログインしている利用者は {@link UserIdLoggingFilter} が入れる。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** MDC の名前。 */
    public static final String REQUEST_ID = "request_id";

    /** ALB のヘルスチェックが数秒ごとに呼ぶので、アクセスログに出さない。 */
    static final String HEALTH_PATH = "/actuator/health";

    /** 受け取る ID の形。ログを壊す文字（改行など）や長すぎる値は使わず、作り直す。 */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdOf(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!HEALTH_PATH.equals(request.getRequestURI())) {
                logAccess(request, response, System.nanoTime() - start);
            }
            MDC.remove(REQUEST_ID);
            MDC.remove(UserIdLoggingFilter.USER_ID);
        }
    }

    static String requestIdOf(String header) {
        return header != null && VALID_ID.matcher(header).matches() ? header : UUID.randomUUID().toString();
    }

    private static void logAccess(HttpServletRequest request, HttpServletResponse response, long durationNanos) {
        LOG.atInfo()
                .addKeyValue("http.method", request.getMethod())
                .addKeyValue("http.url_details.path", request.getRequestURI())
                .addKeyValue("http.status_code", response.getStatus())
                .addKeyValue("duration", durationNanos)
                .addKeyValue("network.client.ip", request.getRemoteAddr())
                .log("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), durationNanos / 1_000_000);
    }
}
