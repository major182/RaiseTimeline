package com.raisetimeline.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.config.TimelineProperties;
import com.raisetimeline.post.Post;
import com.raisetimeline.post.PostRepository;
import com.raisetimeline.post.PostResponse;
import com.raisetimeline.post.PostViews;
import com.raisetimeline.post.TestPosts;
import com.raisetimeline.timeline.TimelineQueries.FeedRow;
import com.raisetimeline.timeline.TimelineQueries.LikeRow;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserRepository;
import com.raisetimeline.user.UserSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * フォロー中タブの業務ルールの単体テスト（BR-50-3・BR-52）。DB と SQL（TimelineQueries）は偽物にする。
 * どの投稿をハイライトに選ぶか（反応の多い順・0 件を除く・最大 3 件）は SQL で決まるので、結合テスト（IT-TL-03）で確かめる。
 */
@ExtendWith(MockitoExtension.class)
class TimelineServiceUnitTests {

    private static final long ME = 1L;
    private static final long STRANGER = 2L;
    private static final Duration HIGHLIGHT_AFTER = Duration.ofHours(6);

    @Mock
    private PostRepository posts;

    @Mock
    private UserRepository users;

    @Mock
    private PostViews views;

    @Mock
    private TimelineQueries queries;

    private Cursors cursors;
    private TimelineService service;

    @BeforeEach
    void setUp() {
        cursors = new Cursors(JsonMapper.builder().build());
        service = new TimelineService(
                posts, users, views, queries, cursors, new TimelineProperties(HIGHLIGHT_AFTER, 3));
    }

    private static User user(long id, String username, Instant lastViewedAt) {
        User user = User.register(username, username + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "lastTimelineViewedAt", lastViewedAt);
        return user;
    }

    private void 前回開いた時刻(Instant last) {
        when(users.findById(ME)).thenReturn(Optional.of(user(ME, "me_user", last)));
    }

    /** 投稿カードの形にする（投稿者が自分か・フォロー中かだけを決める）。 */
    private static PostResponse card(Post post, boolean mine, boolean followed) {
        UserSummary author = new UserSummary(post.getUserId(), "u" + post.getUserId(), "u", null, "", followed, mine);
        return new PostResponse(
                post.getId(), author, post.getBody(), List.of(), post.getCreatedAt(), null, 0, 0, false, null, mine);
    }

    // --- 留守中のハイライト（BR-52。境界値） ---

    @Test
    @DisplayName("UT-TL-01 初めて開いたとき（前回の時刻がない）はハイライトを出さず、時刻だけ記録する")
    void 初めて開く() {
        前回開いた時刻(null);

        FollowingTimelineResponse response = service.following(null, ME);

        assertThat(response.highlights()).isEmpty();
        verify(users).updateLastTimelineViewedAt(eq(ME), any());
        verify(queries, never()).highlights(anyLong(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("UT-TL-01 前回から 6 時間たっていなければ、ハイライトを出さない")
    void 六時間未満() {
        前回開いた時刻(Instant.now().minus(HIGHLIGHT_AFTER).plusSeconds(60));

        service.following(null, ME);

        verify(queries, never()).highlights(anyLong(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("UT-TL-01 前回から 6 時間以上たっていれば、前回から今までの投稿を最大 3 件選び、一覧の上に出す")
    void 六時間以上() {
        Instant last = Instant.now().minus(HIGHLIGHT_AFTER).minusSeconds(60);
        前回開いた時刻(last);
        Post highlight = TestPosts.entity(50L, STRANGER, "反応の多い投稿");
        when(queries.highlights(eq(ME), eq(last), any(), eq(3))).thenReturn(List.of(50L));
        when(posts.findAllById(List.of(50L))).thenReturn(List.of(highlight));
        when(views.of(List.of(highlight), ME)).thenReturn(List.of(card(highlight, false, true)));

        FollowingTimelineResponse response = service.following(null, ME);

        assertThat(response.highlights()).extracting(PostResponse::id).containsExactly(50L);
        // ハイライトに出した投稿は、下の一覧から除く（BR-52-1）
        verify(queries).feed(eq(ME), any(), isNull(), eq(List.of(50L)), eq(Cursors.PAGE_SIZE + 1));
    }

    @Test
    @DisplayName("UT-TL-02 続きの読み込み（カーソルあり）では、ハイライトを判定せず、時刻も記録しない")
    void 続きの読み込み() {
        Instant base = Instant.parse("2026-10-18T00:00:00Z");
        Instant at = base.minusSeconds(600);
        String cursor = cursors.encode(new FollowingCursor(base, at, 99L, List.of(50L)));

        FollowingTimelineResponse response = service.following(cursor, ME);

        assertThat(response.highlights()).isEmpty();
        verify(users, never()).findById(anyLong());
        verify(users, never()).updateLastTimelineViewedAt(anyLong(), any());
        // 最初の読み込みと同じ基準の時刻・除く投稿で、続きから読む（BR-53）
        verify(queries).feed(ME, base, new FeedRow(99L, at), List.of(50L), Cursors.PAGE_SIZE + 1);
    }

    // --- いいね経由の投稿（BR-50-3） ---

    @Test
    @DisplayName("UT-TL-03 いいね経由の表示は、最新のいいねの人の名前と、ほかの人数（1 人なら 0、3 人なら 2）")
    void いいね経由の表示() {
        前回開いた時刻(null);
        Post byThree = TestPosts.entity(60L, STRANGER, "3 人がいいね");
        Post byOne = TestPosts.entity(61L, STRANGER, "1 人がいいね");
        Post mine = TestPosts.entity(62L, ME, "自分の投稿");
        when(queries.feed(eq(ME), any(), isNull(), anyList(), anyInt()))
                .thenReturn(List.of(new FeedRow(60L, Instant.now()), new FeedRow(61L, Instant.now()), new FeedRow(62L, Instant.now())));
        when(posts.findAllById(List.of(60L, 61L, 62L))).thenReturn(List.of(byThree, byOne, mine));
        when(views.of(List.of(byThree, byOne, mine), ME))
                .thenReturn(List.of(card(byThree, false, false), card(byOne, false, false), card(mine, true, false)));
        // 自分の投稿（62）は、いいね経由の候補にしない
        when(queries.followeeLikes(eq(ME), eq(List.of(60L, 61L)), any()))
                .thenReturn(List.of(
                        new LikeRow(60L, 30L), new LikeRow(60L, 31L), new LikeRow(60L, 32L), new LikeRow(61L, 31L)));
        when(users.findAllById(List.of(30L, 31L)))
                .thenReturn(List.of(user(30L, "yamada", null), user(31L, "suzuki", null)));

        List<PostResponse> items = service.following(null, ME).items();

        assertThat(items.get(0).likedVia()).isEqualTo(new PostResponse.LikedVia("yamada", "yamada", 2));
        assertThat(items.get(1).likedVia()).isEqualTo(new PostResponse.LikedVia("suzuki", "suzuki", 0));
        assertThat(items.get(2).likedVia()).isNull();
    }

    @Test
    @DisplayName("UT-TL-02 一覧を読む間に消された投稿は、飛ばして並び順を保つ")
    void 消された投稿を飛ばす() {
        前回開いた時刻(null);
        Post first = TestPosts.entity(70L, ME, "1 件目");
        Post third = TestPosts.entity(72L, ME, "3 件目");
        when(queries.feed(eq(ME), any(), isNull(), anyList(), anyInt()))
                .thenReturn(List.of(new FeedRow(70L, Instant.now()), new FeedRow(71L, Instant.now()), new FeedRow(72L, Instant.now())));
        // 71 は消されていて見つからない。DB は ID の順に返すとは限らない
        when(posts.findAllById(List.of(70L, 71L, 72L))).thenReturn(List.of(third, first));

        service.following(null, ME);

        verify(views).of(List.of(first, third), ME);
    }
}
