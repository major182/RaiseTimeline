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

/** フォロー中タブの、自分とフォロー中の人の投稿（F-TL-01、BR-50-2）。いいね経由・ハイライトは別のテスト。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FollowingTimelineTests {

    private static final String PATH = "/api/timeline/following";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private long friend;
    private long stranger;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
        friend = TestUsers.insert(jdbc, "friend");
        stranger = TestUsers.insert(jdbc, "stranger");
        TestUsers.follow(jdbc, me.id(), friend);
    }

    @Test
    void 自分とフォロー中の人の投稿だけを新しい順に出す() throws Exception {
        long mine = TestPosts.insert(jdbc, me.id(), "自分", BASE.plusSeconds(2));
        long friends = TestPosts.insert(jdbc, friend, "フォロー中", BASE.plusSeconds(3));
        TestPosts.insert(jdbc, stranger, "フォローしていない人", BASE.plusSeconds(4));
        long older = TestPosts.insert(jdbc, friend, "古い", BASE.plusSeconds(1));
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactly(friends, mine, older);
    }

    @Test
    void 二十件ずつ読み同じ時刻があっても重複も欠落もしない() throws Exception {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            long author = i % 2 == 0 ? friend : me.id();
            expected.addFirst(TestPosts.insert(jdbc, author, "p" + i, i < 25 ? BASE : BASE.plusSeconds(i)));
        }
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactlyElementsOf(expected);
    }

    @Test
    void フォローをやめた人の投稿は出さない() throws Exception {
        TestPosts.insert(jdbc, friend, "フォロー中", BASE);
        jdbc.update("DELETE FROM follows");
        assertThat(Timelines.readAll(me.client(), PATH)).isEmpty();
    }

    @Test
    void 誰もフォローしていなくても自分の投稿は出す() throws Exception {
        jdbc.update("DELETE FROM follows");
        long mine = TestPosts.insert(jdbc, me.id(), "自分", BASE);
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactly(mine);
    }

    @Test
    void 形は一覧にハイライトを足したもの() throws Exception {
        me.client()
                .get(PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highlights").isArray())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void 壊れたカーソルは400() throws Exception {
        me.client().get(PATH, "cursor", "e30").andExpect(status().isBadRequest()); // e30 は {} （値がない）
    }
}
