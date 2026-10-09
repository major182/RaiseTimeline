package com.raisetimeline.timeline;

import static com.raisetimeline.timeline.Timelines.BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** フォロー中タブのいいね経由の投稿（F-TL-07、BR-50-3、DB 設計書 5.2・5.5）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LikedViaTimelineTests {

    private static final String PATH = "/api/timeline/following";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private long friend1;
    private long friend2;
    private long stranger;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
        friend1 = TestUsers.insert(jdbc, "friend_1", "山田");
        friend2 = TestUsers.insert(jdbc, "friend_2", "佐藤");
        stranger = TestUsers.insert(jdbc, "stranger");
        TestUsers.follow(jdbc, me.id(), friend1);
        TestUsers.follow(jdbc, me.id(), friend2);
    }

    @Test
    void フォロー中の人がいいねした投稿をいいねの時刻の位置に混ぜる() throws Exception {
        long liked = TestPosts.insert(jdbc, stranger, "フォローしていない人", BASE); // 投稿日時は一番古い
        long friendPost1 = TestPosts.insert(jdbc, friend1, "1", BASE.plusSeconds(10));
        long friendPost2 = TestPosts.insert(jdbc, friend1, "2", BASE.plusSeconds(30));
        Timelines.like(jdbc, liked, friend1, BASE.plusSeconds(20)); // いいねは 2 つの投稿の間
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactly(friendPost2, liked, friendPost1);
    }

    @Test
    void 一人がいいねしたら表示名を添える() throws Exception {
        long liked = TestPosts.insert(jdbc, stranger, "本文", BASE);
        Timelines.like(jdbc, liked, friend1, BASE.plusSeconds(1));
        me.client()
                .get(PATH)
                .andExpect(jsonPath("$.items[0].id").value(liked))
                .andExpect(jsonPath("$.items[0].likedVia.displayName").value("山田"))
                .andExpect(jsonPath("$.items[0].likedVia.username").value("friend_1"))
                .andExpect(jsonPath("$.items[0].likedVia.othersCount").value(0));
    }

    @Test
    void 複数人なら一番新しい人とほかの人数を添え投稿は1回だけ出す() throws Exception {
        long liked = TestPosts.insert(jdbc, stranger, "本文", BASE);
        Timelines.like(jdbc, liked, friend1, BASE.plusSeconds(1));
        Timelines.like(jdbc, liked, friend2, BASE.plusSeconds(2));
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactly(liked);
        me.client()
                .get(PATH)
                .andExpect(jsonPath("$.items[0].likedVia.displayName").value("佐藤"))
                .andExpect(jsonPath("$.items[0].likedVia.othersCount").value(1));
    }

    @Test
    void フォローしていない人のいいねは数えない() throws Exception {
        long liked = TestPosts.insert(jdbc, stranger, "本文", BASE);
        long other = TestUsers.insert(jdbc, "other_one");
        Timelines.like(jdbc, liked, other, BASE.plusSeconds(1));
        assertThat(Timelines.readAll(me.client(), PATH)).isEmpty();

        Timelines.like(jdbc, liked, friend1, BASE.plusSeconds(2));
        me.client().get(PATH).andExpect(jsonPath("$.items[0].likedVia.othersCount").value(0));
    }

    @Test
    void 自分やフォロー中の人の投稿はいいねされてもいいね経由にしない() throws Exception {
        long mine = TestPosts.insert(jdbc, me.id(), "自分", BASE);
        long friends = TestPosts.insert(jdbc, friend2, "フォロー中", BASE.plusSeconds(1));
        Timelines.like(jdbc, mine, friend1, BASE.plusSeconds(5));
        Timelines.like(jdbc, friends, friend1, BASE.plusSeconds(6));
        // 投稿日時の位置に1回だけ出て、いいねの時刻で上に移動しない
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactly(friends, mine);
        me.client()
                .get(PATH)
                .andExpect(jsonPath("$.items[0].likedVia").isEmpty())
                .andExpect(jsonPath("$.items[1].likedVia").isEmpty());
    }

    @Test
    void フォローしている人の投稿はほかのタブではいいね経由にならない() throws Exception {
        long liked = TestPosts.insert(jdbc, stranger, "本文", BASE);
        Timelines.like(jdbc, liked, friend1, BASE.plusSeconds(1));
        me.client().get("/api/timeline/all").andExpect(jsonPath("$.items[0].likedVia").isEmpty());
        me.client().get("/api/posts/" + liked).andExpect(jsonPath("$.likedVia").isEmpty());
    }

    @Test
    void 投稿者をフォローするといいね経由ではなく投稿日時の位置に出る() throws Exception {
        long liked = TestPosts.insert(jdbc, stranger, "本文", BASE);
        long friendPost = TestPosts.insert(jdbc, friend1, "フォロー中", BASE.plusSeconds(10));
        Timelines.like(jdbc, liked, friend1, BASE.plusSeconds(20));
        TestUsers.follow(jdbc, me.id(), stranger);
        assertThat(Timelines.readAll(me.client(), PATH)).containsExactly(friendPost, liked);
    }
}
