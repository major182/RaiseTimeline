package com.raisetimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.raisetimeline.auth.AuthenticatedUser;
import jakarta.servlet.ServletException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** リクエストの ID・アクセスログ・利用者の ID の単体テスト（ログ設計書 4 章）。 */
@DisplayName("UT-CO-05 リクエストの ID とアクセスログ")
class RequestLoggingFilterTests {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static Map<String, Object> pairs(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(p -> p.key, p -> p.value));
    }

    @Test
    void 受け取ったIDを使いMDCと応答に入れてアクセスログを出す() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.addHeader("X-Request-Id", "req-1.A_b");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];
        filter.doFilter(request, response, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {}) {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen[0] = MDC.get(RequestLoggingFilter.REQUEST_ID);
                ((MockHttpServletResponse) res).setStatus(201);
            }
        });

        assertThat(seen[0]).isEqualTo("req-1.A_b");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-1.A_b");
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).startsWith("GET /api/posts 201 ");
        assertThat(pairs(event))
                .containsEntry("http.method", "GET")
                .containsEntry("http.url_details.path", "/api/posts")
                .containsEntry("http.status_code", 201)
                .containsEntry("network.client.ip", "203.0.113.5")
                .containsKey("duration");
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID)).isNull(); // 終わったら消す
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a\nb", "a b", "<script>", "12345678901234567890123456789012345678901234567890123456789012345"})
    void IDがないか形が正しくなければ作り直す(String header) {
        assertThat(RequestLoggingFilter.requestIdOf(header))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void IDは64文字まで受け取る() {
        String id = "a".repeat(64);
        assertThat(RequestLoggingFilter.requestIdOf(id)).isEqualTo(id);
    }

    @Test
    void ヘルスチェックはアクセスログに出さない() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void 例外が起きてもアクセスログを出しMDCを消す() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws ServletException {
                MDC.put(UserIdLoggingFilter.USER_ID, "7");
                throw new ServletException("こわれた");
            }
        };
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);
        assertThat(appender.list).hasSize(1);
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID)).isNull();
        assertThat(MDC.get(UserIdLoggingFilter.USER_ID)).isNull();
    }

    @Test
    void ログインしていれば利用者のIDをMDCに入れる() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(7L), null, List.of()));
        String[] seen = new String[1];
        new UserIdLoggingFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> seen[0] = MDC.get("usr.id"));
        assertThat(seen[0]).isEqualTo("7");
    }

    @Test
    void ログインしていなければ利用者のIDを入れない() throws Exception {
        String[] seen = {"x"};
        new UserIdLoggingFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> seen[0] = MDC.get("usr.id"));
        assertThat(seen[0]).isNull();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of())); // 利用者でない主体
        new UserIdLoggingFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> seen[0] = MDC.get("usr.id"));
        assertThat(seen[0]).isNull();
    }
}
