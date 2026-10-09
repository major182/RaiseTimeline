package com.raisetimeline.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 留守中のハイライト（F-TL-08、BR-52・52-1・55、DB 設計書 5.3）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HighlightTests {

    private static final String PATH = "/api/timeline/following";

    /** 7 時間前に最後に開いた（6 時間以上たっている）。 */
    private static final Duration AWAY = Duration.ofHours(7);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private long friend;
    private long other;
    private Instant lastViewed;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
        friend = TestUsers.insert(jdbc, "friend");
        other = TestUsers.insert(jdbc, "other_one");
        TestUsers.follow(jdbc, me.id(), friend);
        lastViewed = Instant.now().minus(AWAY);
        Timelines.setLastViewed(jdbc, me.id(), lastViewed);
    }

    /** 留守の間（前回開いた時刻の後）のフォロー中の人の投稿を作る。 */
    private long awayPost(int minutesAfter) {
        return TestPosts.insert(jdbc, friend, "留守中", lastViewed.plus(Duration.ofMinutes(minutesAfter)));
    }

    private void likes(long postId, int count) {
        for (int i = 0; i < count; i++) {
            long liker = TestUsers.insert(jdbc, "liker_%d_%d".formatted(postId, i));
            Timelines.like(jdbc, postId, liker, Instant.now().minusSeconds(60));
        }
    }

    @Test
    void 反応の多い順に最大3件をハイライトに出す() throws Exception {
        long a = awayPost(10);
        long b = awayPost(20);
        long c = awayPost(30);
        long d = awayPost(40);
        likes(a, 1); // 点 1
        likes(b, 3); // 点 3
        Timelines.comment(jdbc, c, other); // コメントは 2 点
        Timelines.comment(jdbc, c, other); // 点 4
        likes(d, 2); // 点 2
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).containsExactly(c, b, d);
    }

    @Test
    void 同じ点なら新しい順() throws Exception {
        long older = awayPost(10);
        long newer = awayPost(20);
        likes(older, 1);
        likes(newer, 1);
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).containsExactly(newer, older);
    }

    @Test
    void 反応がない投稿は出さない() throws Exception {
        awayPost(10);
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).isEmpty();
    }

    @Test
    void 自分の投稿と前回より前の投稿とフォローしていない人の投稿は出さない() throws Exception {
        long mine = TestPosts.insert(jdbc, me.id(), "自分", lastViewed.plusSeconds(60));
        long before = TestPosts.insert(jdbc, friend, "前回より前", lastViewed.minusSeconds(60));
        long strangers = TestPosts.insert(jdbc, other, "フォローしていない人", lastViewed.plusSeconds(60));
        likes(mine, 1);
        likes(before, 1);
        likes(strangers, 1);
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).isEmpty();
    }

    @Test
    void 前回から6時間たっていなければ出さない() throws Exception {
        Timelines.setLastViewed(jdbc, me.id(), Instant.now().minus(Duration.ofHours(5)));
        long post = TestPosts.insert(jdbc, friend, "最近", Instant.now().minus(Duration.ofHours(1)));
        likes(post, 5);
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).isEmpty();
    }

    @Test
    void はじめて開いたときは出さず開いた時刻を記録する() throws Exception {
        jdbc.update("UPDATE users SET last_timeline_viewed_at = NULL WHERE id = ?", me.id());
        likes(awayPost(10), 1);
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).isEmpty();
        assertThat(lastViewedInDb()).isAfter(Instant.now().minusSeconds(60));
    }

    @Test
    void ハイライトに出した投稿は下の一覧に出さない() throws Exception {
        long highlighted = awayPost(10);
        long normal = awayPost(20);
        likes(highlighted, 1);
        Timelines.Page page = Timelines.read(me.client(), PATH, null);
        assertThat(page.highlights()).containsExactly(highlighted);
        assertThat(page.items()).containsExactly(normal);
    }

    @Test
    void 続きの読み込みでもハイライトに出した投稿は出さずハイライトは空() throws Exception {
        long highlighted = awayPost(1);
        likes(highlighted, 1);
        for (int i = 0; i < 25; i++) {
            awayPost(2 + i);
        }
        Timelines.Page first = Timelines.read(me.client(), PATH, null);
        Timelines.Page second = Timelines.read(me.client(), PATH, first.nextCursor());
        assertThat(first.highlights()).containsExactly(highlighted);
        assertThat(second.highlights()).isEmpty();
        assertThat(first.items()).hasSize(20).doesNotContain(highlighted);
        assertThat(second.items()).hasSize(5).doesNotContain(highlighted);
    }

    @Test
    void 開いたら時刻を今にするので続けて開いても出さない() throws Exception {
        likes(awayPost(10), 1);
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).hasSize(1);
        assertThat(lastViewedInDb()).isAfter(Instant.now().minusSeconds(60));
        assertThat(Timelines.read(me.client(), PATH, null).highlights()).isEmpty();
    }

    @Test
    void 続きの読み込みでは開いた時刻を変えない() throws Exception {
        for (int i = 0; i < 25; i++) {
            awayPost(i);
        }
        Timelines.Page first = Timelines.read(me.client(), PATH, null);
        Instant afterFirst = lastViewedInDb();
        Timelines.read(me.client(), PATH, first.nextCursor());
        assertThat(lastViewedInDb()).isEqualTo(afterFirst);
    }

    @Test
    void 全体タブを開いても開いた時刻は変えない() throws Exception {
        Instant before = lastViewedInDb();
        Timelines.read(me.client(), "/api/timeline/all", null);
        assertThat(lastViewedInDb()).isEqualTo(before);
    }

    private Instant lastViewedInDb() {
        return jdbc.queryForObject(
                        "SELECT last_timeline_viewed_at FROM users WHERE id = ?", Timestamp.class, me.id())
                .toInstant();
    }
}
