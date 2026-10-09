package com.raisetimeline.timeline;

import static com.raisetimeline.timeline.Timelines.BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
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

/** 全体タブ（F-TL-05、BR-50-1・53、DB 設計書 5.1）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AllTimelineTests {

    private static final String PATH = "/api/timeline/all";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private long stranger;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
        stranger = TestUsers.insert(jdbc, "stranger"); // フォローしていない人
    }

    @Test
    void フォローしていない人も含め全員の投稿を新しい順に出す() throws Exception {
        long a = TestPosts.insert(jdbc, stranger, "2", BASE.plusSeconds(2));
        long b = TestPosts.insert(jdbc, me.id(), "3", BASE.plusSeconds(3));
        long c = TestPosts.insert(jdbc, stranger, "1", BASE.plusSeconds(1));
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactly(b, a, c);
    }

    @Test
    void 二十件ずつ読み同じ時刻があっても重複も欠落もしない() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            expected.addFirst(TestPosts.insert(jdbc, stranger, "p" + i, i < 25 ? BASE : BASE.plusSeconds(i)));
        }
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactlyElementsOf(expected);
    }

    @Test
    void 読んでいる間に増えた投稿は続きに出てこない() throws Exception {
        for (int i = 0; i < 25; i++) {
            TestPosts.insert(jdbc, stranger, "p" + i, BASE.plusSeconds(i));
        }
        Timelines.Page first = Timelines.read(me.client(), PATH, null);
        long added = TestPosts.createId(me.client(), "途中で増えた投稿"); // 今の時刻なので一番上に入る
        Timelines.Page second = Timelines.read(me.client(), PATH, first.nextCursor());
        assertThat(second.items()).hasSize(5).doesNotContain(added).doesNotContainAnyElementsOf(first.items());
    }

    @Test
    void 投稿カードの形で返す() throws Exception {
        TestPosts.insert(jdbc, stranger, "本文", BASE);
        me.client()
                .get(PATH)
                .andExpect(jsonPath("$.items[0].body").value("本文"))
                .andExpect(jsonPath("$.items[0].author.username").value("stranger"))
                .andExpect(jsonPath("$.items[0].isMine").value(false))
                .andExpect(jsonPath("$.items[0].likedVia").isEmpty())
                .andExpect(jsonPath("$.highlights").doesNotExist());
    }

    @Test
    void 投稿がなければ空() throws Exception {
        me.client()
                .get(PATH)
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void 壊れたカーソルは400() throws Exception {
        me.client().get(PATH, "cursor", "broken").andExpect(status().isBadRequest());
    }
}
