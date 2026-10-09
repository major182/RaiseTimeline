package com.raisetimeline;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 画面（ブラウザ）と同じように API を呼ぶテスト用の道具（API 設計書 2.2）。
 * - 応答の accessToken を覚え、次の呼び出しで Authorization: Bearer ヘッダーに入れる（画面のメモリの代わり）
 * - 応答の Cookie（REFRESH_TOKEN）を覚え、次の呼び出しで送る（ブラウザの代わり）
 * - X-Requested-With ヘッダーを付ける（画面の request 関数と同じ）
 */
public class ApiClient {

    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private String accessToken;
    private boolean sendRequestedWith = true;

    public ApiClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public ResultActions get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path));
    }

    /**
     * クエリの値を付けて GET する（名前と値を交互に並べる）。値は MockMvc がエンコードする。
     * パスに直接 {@code ?q=...} を書くと、エンコード済みの「%」がもう一度エンコードされてしまう。
     */
    public ResultActions get(String path, String... params) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(path);
        for (int i = 0; i + 1 < params.length; i += 2) {
            if (params[i + 1] != null) {
                builder.param(params[i], params[i + 1]);
            }
        }
        return perform(builder);
    }

    public ResultActions post(String path, String json) throws Exception {
        return perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions post(String path) throws Exception {
        return perform(MockMvcRequestBuilders.post(path));
    }

    public ResultActions put(String path, String json) throws Exception {
        return perform(MockMvcRequestBuilders.put(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /**
     * multipart/form-data で POST する（投稿の作成など）。フォームの項目は名前と値を交互に並べる。
     *
     * @param files 添付するファイル（なければ空の配列）
     */
    public ResultActions multipart(String path, MockMultipartFile[] files, String... fields) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(path);
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        for (int i = 0; i + 1 < fields.length; i += 2) {
            builder.param(fields[i], fields[i + 1]);
        }
        return perform(builder);
    }

    public ResultActions patch(String path, String json) throws Exception {
        return perform(MockMvcRequestBuilders.patch(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions delete(String path) throws Exception {
        return perform(MockMvcRequestBuilders.delete(path));
    }

    /** X-Requested-With を付けずに送る（CSRF の対策を確かめるため）。 */
    public ApiClient withoutRequestedWith() {
        sendRequestedWith = false;
        return this;
    }

    public String accessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Cookie cookie(String name) {
        return cookies.get(name);
    }

    public void setCookie(Cookie cookie) {
        cookies.put(cookie.getName(), cookie);
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> builder) throws Exception {
        if (accessToken != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        if (sendRequestedWith) {
            builder.header("X-Requested-With", "RaiseTimeline");
        }
        if (!cookies.isEmpty()) {
            builder.cookie(cookies.values().toArray(Cookie[]::new));
        }
        ResultActions result = mvc.perform(builder);
        remember(result.andReturn());
        return result;
    }

    private void remember(MvcResult result) throws Exception {
        for (Cookie cookie : result.getResponse().getCookies()) {
            // 期限 0 の Cookie は「消して」という指示（ログアウトなど）
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        String body = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() < 300 && body.contains("\"accessToken\"")) {
            accessToken = JsonPath.read(body, "$.accessToken");
        }
    }
}
