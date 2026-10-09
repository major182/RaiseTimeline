package com.raisetimeline.timeline;

import static com.raisetimeline.timeline.Timelines.BASE;
import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.TestUsers;
import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.post.TestPosts;
import java.time.Instant;
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

/**
 * フォロー中タブを読み進める間に、投稿やいいねが増えても、重複・欠落しないこと（BR-53、DB 設計書 5.2 の基準の時刻）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FollowingStabilityTests {

    private static final String PATH = "/api/timeline/following";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private long friend;
    private long stranger;
    private List<Long> friendPosts;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
        friend = TestUsers.insert(jdbc, "friend");
        stranger = TestUsers.insert(jdbc, "stranger");
        TestUsers.follow(jdbc, me.id(), friend);
        friendPosts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            friendPosts.addFirst(TestPosts.insert(jdbc, friend, "p" + i, BASE.plusSeconds(i * 10L)));
        }
    }

    @Test
    void 読み進める間に増えた投稿は続きに出てこない() throws Exception {
        Timelines.Page first = Timelines.read(me.client(), PATH, null);
        long added = TestPosts.insert(jdbc, friend, "途中で増えた", Instant.now());
        Timelines.Page second = Timelines.read(me.client(), PATH, first.nextCursor());
        List<Long> all = new ArrayList<>(first.items());
        all.addAll(second.items());
        assertThat(all).containsExactlyElementsOf(friendPosts).doesNotContain(added);
    }

    @Test
    void 読み進める間についたいいねで投稿が移動したり重複したりしない() throws Exception {
        // 2 ページ目に出る位置（古い方）に、いいね経由の投稿を置く
        long liked = TestPosts.insert(jdbc, stranger, "いいね経由", BASE.minusSeconds(100));
        Timelines.like(jdbc, liked, friend, BASE.plusSeconds(5)); // 一番古い friend の投稿の少し後
        Timelines.Page first = Timelines.read(me.client(), PATH, null);
        assertThat(first.items()).doesNotContain(liked);

        // 1 ページ目を読んだ後に、同じ投稿へのいいねが付き直した（新しい時刻）
        jdbc.update("DELETE FROM likes");
        Timelines.like(jdbc, liked, friend, Instant.now());
        Timelines.Page second = Timelines.read(me.client(), PATH, first.nextCursor());

        // 基準の時刻より後のいいねは使わないので、今回の読み込みでは消えるが、1 ページ目に重複して出ることはない
        List<Long> all = new ArrayList<>(first.items());
        all.addAll(second.items());
        assertThat(all).doesNotHaveDuplicates().containsAll(friendPosts);
    }

    @Test
    void 基準の時刻より前のいいねは位置が変わらない() throws Exception {
        long liked = TestPosts.insert(jdbc, stranger, "いいね経由", BASE.minusSeconds(100));
        Timelines.like(jdbc, liked, friend, BASE.plusSeconds(5));
        Timelines.Page first = Timelines.read(me.client(), PATH, null);
        // 1 ページ目の後に、別のフォロー中の人が新しくいいねしても、2 ページ目の位置のまま
        long friend2 = TestUsers.insert(jdbc, "friend_2");
        TestUsers.follow(jdbc, me.id(), friend2);
        Timelines.like(jdbc, liked, friend2, Instant.now());
        Timelines.Page second = Timelines.read(me.client(), PATH, first.nextCursor());
        assertThat(first.items()).doesNotContain(liked);
        assertThat(second.items()).contains(liked);
    }
}
