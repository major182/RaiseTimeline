package com.raisetimeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raisetimeline.TestUsers.TestUser;
import com.raisetimeline.post.TestPosts;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 一覧で、行ごとに SQL を発行しない（N+1 にしない。NF-PE-02、DB 設計書 5.5・5.9）。
 * 同じ形のデータを 1 件と 20 件（1 ページ分）で作り、SQL の数が変わらないことを確かめる。
 * 行ごとに作者・画像・いいね・コメントの数を取るようになると、20 件のほうが多くなって失敗する。
 */
@DisplayName("IT-CO-06 一覧の SQL の数")
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SqlCounter.Config.class})
class QueryCountTests {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private TestUser me;
    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM users");
        me = TestUsers.signup(mvc, "me_user");
    }

    private long user() {
        return TestUsers.insert(jdbc, "user%03d".formatted(sequence++)); // ユーザー名は 4 文字以上（BR-02）
    }

    private void like(long postId, long userId) {
        jdbc.update("INSERT INTO likes (post_id, user_id) VALUES (?, ?)", postId, userId);
    }

    private long comment(long postId, long userId, Long parentId, int second) {
        return jdbc.queryForObject(
                "INSERT INTO comments (post_id, user_id, parent_id, body, created_at) VALUES (?, ?, ?, 'c', ?)"
                        + " RETURNING id",
                Long.class,
                postId,
                userId,
                parentId,
                Timestamp.from(BASE.plusSeconds(second)));
    }

    /**
     * フォローしている人の投稿を n 件作る（作者は別々。画像・いいね・コメント・返信つき）。
     * あわせて、フォローしていない人の投稿を、フォローしている人がいいねする（いいね経由）。
     */
    private long timelineData(int n) {
        long liker = user();
        TestUsers.follow(jdbc, me.id(), liker);
        long lastPost = 0;
        for (int i = 0; i < n; i++) {
            long author = user();
            TestUsers.follow(jdbc, me.id(), author);
            lastPost = TestPosts.insert(jdbc, author, "投稿" + i, BASE.plusSeconds(i));
            jdbc.update(
                    "INSERT INTO post_images (post_id, position, storage_key, content_type, size_bytes, width, height)"
                            + " VALUES (?, 1, ?, 'image/png', 1, 1, 1)",
                    lastPost,
                    "test/" + lastPost + ".png");
            like(lastPost, liker);
            long parent = comment(lastPost, liker, null, i);
            comment(lastPost, author, parent, i);
        }
        long stranger = user();
        like(TestPosts.insert(jdbc, stranger, "いいね経由", BASE), liker);
        return lastPost;
    }

    private int sqlOf(String path) throws Exception {
        return SqlCounter.count(() -> me.client().get(path).andExpect(status().isOk()));
    }

    @Test
    void 全体タブとフォロー中タブとプロフィールの投稿一覧() throws Exception {
        timelineData(1);
        long author = jdbc.queryForObject("SELECT user_id FROM posts WHERE body = '投稿0'", Long.class);
        int all1 = sqlOf("/api/timeline/all");
        int following1 = sqlOf("/api/timeline/following");
        int profile1 = sqlOf("/api/users/" + author + "/posts");
        assertThat(all1).isPositive(); // 数え方が効いている（0 なら数えられていない）

        setUp();
        timelineData(20);
        author = jdbc.queryForObject("SELECT user_id FROM posts WHERE body = '投稿0'", Long.class);
        assertThat(sqlOf("/api/timeline/all")).isEqualTo(all1);
        assertThat(sqlOf("/api/timeline/following")).isEqualTo(following1);
        assertThat(sqlOf("/api/users/" + author + "/posts")).isEqualTo(profile1);
    }

    /** 1 つの投稿に n 件のコメント（作者は別々。それぞれ返信つき）と、1 つのコメントに n 件の返信を作る。 */
    private long[] commentData(int n) {
        long postId = TestPosts.insert(jdbc, me.id(), "投稿", BASE);
        long withReplies = comment(postId, me.id(), null, 0);
        for (int i = 0; i < n; i++) {
            long author = user();
            long parent = comment(postId, author, null, i + 1);
            comment(postId, me.id(), parent, i + 1);
            comment(postId, author, withReplies, i + 1);
        }
        return new long[] {postId, withReplies};
    }

    @Test
    void コメントの一覧と返信の一覧() throws Exception {
        long[] one = commentData(1);
        int comments1 = sqlOf("/api/posts/" + one[0] + "/comments");
        int replies1 = sqlOf("/api/comments/" + one[1] + "/replies");
        assertThat(comments1).isPositive(); // 数え方が効いている（0 なら数えられていない）

        setUp();
        long[] twenty = commentData(20);
        assertThat(sqlOf("/api/posts/" + twenty[0] + "/comments")).isEqualTo(comments1);
        assertThat(sqlOf("/api/comments/" + twenty[1] + "/replies")).isEqualTo(replies1);
    }
}
