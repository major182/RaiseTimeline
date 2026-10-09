package com.raisetimeline;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 画面（ブラウザ）と同じように API を呼ぶテスト用の道具。
 * 返ってきた Cookie（SESSION・XSRF-TOKEN）を覚えて次の呼び出しで送り、GET 以外では CSRF トークンをヘッダーに入れる。
 */
public class ApiClient {

    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    public ApiClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public ResultActions get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path));
    }

    public ResultActions post(String path, String json) throws Exception {
        return perform(withCsrf(MockMvcRequestBuilders.post(path)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions put(String path, String json) throws Exception {
        return perform(withCsrf(MockMvcRequestBuilders.put(path)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public Cookie cookie(String name) {
        return cookies.get(name);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) throws Exception {
        if (!cookies.containsKey("XSRF-TOKEN")) {
            get("/api/auth/csrf");
        }
        return builder.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN").getValue());
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        if (!cookies.isEmpty()) {
            builder.cookie(cookies.values().toArray(Cookie[]::new));
        }
        ResultActions result = mvc.perform(builder);
        for (Cookie cookie : result.andReturn().getResponse().getCookies()) {
            // 期限 0 の Cookie は「消して」という指示（ログアウトなど）
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        return result;
    }
}
