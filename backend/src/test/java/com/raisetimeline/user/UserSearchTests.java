package com.raisetimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 利用者の検索（F-US-03、DB 設計書 5.6）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserSearchTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
    }

    /** 検索の結果を最後まで読み、ユーザー名を順番に返す。 */
    private List<String> search(String q) throws Exception {
        List<String> names = new ArrayList<>();
        String cursor = null;
        do {
            String body = me.client()
                    .get("/api/users/search", "q", q, "cursor", cursor)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            names.addAll(JsonPath.read(body, "$.items[*].username"));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);
        return names;
    }

    @Test
    void ユーザー名と表示名の部分一致で大文字小文字を区別しない() throws Exception {
        TestUsers.insert(jdbc, "Tanaka_1", "たなか");
        TestUsers.insert(jdbc, "yamada", "TANAKA ファン");
        TestUsers.insert(jdbc, "suzuki", "すずき");
        assertThat(search("tanaka")).containsExactlyInAnyOrder("Tanaka_1", "yamada");
        assertThat(search("たな")).containsExactly("Tanaka_1");
    }

    @Test
    void ユーザー名が完全に一致する人を先頭にする() throws Exception {
        TestUsers.insert(jdbc, "raise");
        TestUsers.insert(jdbc, "raise_fan"); // 後から登録したので、普通なら先に来る
        TestUsers.insert(jdbc, "raise_two");
        assertThat(search("RAISE")).first().isEqualTo("raise");
    }

    @Test
    void 完全一致でない人は新しく登録した順() throws Exception {
        TestUsers.insert(jdbc, "abc_1");
        TestUsers.insert(jdbc, "abc_2");
        TestUsers.insert(jdbc, "abc_3");
        assertThat(search("abc_")).containsExactly("abc_3", "abc_2", "abc_1");
    }

    @Test
    void パーセントとアンダースコアはただの文字として探す() throws Exception {
        TestUsers.insert(jdbc, "under_bar");
        TestUsers.insert(jdbc, "nobar", "100%の人");
        TestUsers.insert(jdbc, "plain", "100点の人");
        // エスケープしないと「_」は任意の1文字、「%」は任意の文字列に一致してしまう
        assertThat(search("_")).containsExactlyInAnyOrder("me_user", "under_bar");
        assertThat(search("100%")).containsExactly("nobar");
    }

    @Test
    void 二十件を超えると続きを読める() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String name = "many_%02d".formatted(i);
            TestUsers.insert(jdbc, name);
            expected.addFirst(name);
        }
        assertThat(search("many")).containsExactlyElementsOf(expected);
    }

    @Test
    void 一致しなければ空の一覧() throws Exception {
        assertThat(search("zzz_none")).isEmpty();
    }

    @Test
    void 検索語は1から50文字で前後の空白は除く() throws Exception {
        for (String q : new String[] {"", "   ", "あ".repeat(51)}) {
            me.client()
                    .get("/api/users/search", "q", q)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("q"))
                    .andExpect(jsonPath("$.errors[0].code").value("QUERY_LENGTH"));
        }
        me.client().get("/api/users/search").andExpect(status().isBadRequest());
        assertThat(search("  me_user  ")).containsExactly("me_user");
        me.client()
                .get("/api/users/search", "q", "あ".repeat(50))
                .andExpect(status().isOk());
    }

    @Test
    void 壊れたカーソルは400() throws Exception {
        me.client().get("/api/users/search?q=me&cursor=xyz").andExpect(status().isBadRequest());
    }
}
