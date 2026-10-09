package com.raisetimeline.timeline;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.config.TimelineProperties;
import com.raisetimeline.post.Post;
import com.raisetimeline.post.PostRepository;
import com.raisetimeline.post.PostResponse;
import com.raisetimeline.post.PostViews;
import com.raisetimeline.timeline.TimelineQueries.FeedRow;
import com.raisetimeline.timeline.TimelineQueries.LikeRow;
import com.raisetimeline.user.User;
import com.raisetimeline.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** タイムラインの業務ルール（要件定義書 BR-50〜55、API 設計書 4.3）。 */
@Service
public class TimelineService {

    /** 続きがあるかを知るため、1 件多く取る（{@link Cursors#page}）。 */
    private static final int PAGE_PLUS_ONE = Cursors.PAGE_SIZE + 1;

    private final PostRepository posts;
    private final UserRepository users;
    private final PostViews views;
    private final TimelineQueries queries;
    private final Cursors cursors;
    private final TimelineProperties properties;

    public TimelineService(
            PostRepository posts,
            UserRepository users,
            PostViews views,
            TimelineQueries queries,
            Cursors cursors,
            TimelineProperties properties) {
        this.posts = posts;
        this.users = users;
        this.views = views;
        this.queries = queries;
        this.cursors = cursors;
        this.properties = properties;
    }

    /** 全体タブ（F-TL-05、BR-50-1）。全員の投稿の新しい順。 */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> all(String cursor, long meId) {
        PostCursor after = cursors.decode(cursor, PostCursor.class);
        List<Post> rows = after == null
                ? posts.findLatest(Limit.of(PAGE_PLUS_ONE))
                : posts.findLatestBefore(after.at(), after.id(), Limit.of(PAGE_PLUS_ONE));
        return toPage(rows, meId);
    }

    /** 利用者ごとの投稿一覧（F-TL-03）。 */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> byUser(long userId, String cursor, long meId) {
        if (!users.existsById(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        PostCursor after = cursors.decode(cursor, PostCursor.class);
        List<Post> rows = after == null
                ? posts.findByUser(userId, Limit.of(PAGE_PLUS_ONE))
                : posts.findByUserBefore(userId, after.at(), after.id(), Limit.of(PAGE_PLUS_ONE));
        return toPage(rows, meId);
    }

    /**
     * フォロー中タブ（F-TL-01・07・08）。
     * カーソルなしで呼んだとき（最初の読み込み）だけ、留守中のハイライトを判定し、「最後に開いた時刻」を今にする。
     */
    @Transactional
    public FollowingTimelineResponse following(String cursor, long meId) {
        FollowingCursor after = cursors.decode(cursor, FollowingCursor.class);
        Instant base;
        List<Long> highlightIds;
        List<FeedRow> rows;
        if (after == null) {
            base = Instant.now();
            highlightIds = pickHighlights(meId, base);
            rows = queries.feed(meId, base, null, highlightIds, PAGE_PLUS_ONE);
        } else {
            base = after.base();
            highlightIds = after.highlightIds();
            rows = queries.feed(meId, base, new FeedRow(after.id(), after.at()), highlightIds, PAGE_PLUS_ONE);
        }
        CursorPage<PostResponse> page = cursors.page(
                rows,
                r -> new FollowingCursor(base, r.sortAt(), r.postId(), highlightIds),
                pageRows -> withLikedVia(load(pageRows.stream().map(FeedRow::postId).toList(), meId), meId, base));
        List<PostResponse> highlights = after == null ? load(highlightIds, meId) : List.of();
        return new FollowingTimelineResponse(highlights, page.items(), page.nextCursor());
    }

    /**
     * 留守中のハイライト（BR-52、DB 設計書 5.3）。前回開いてから設定の時間（6 時間）以上たっていれば、
     * その間のフォロー中の人の投稿から、反応の多いものを最大 3 件選ぶ。判定のあと「最後に開いた時刻」を今にする。
     * 記録するのはこの時刻だけで、どの投稿を見たか（表示回数）は記録しない（BR-55）。
     */
    private List<Long> pickHighlights(long meId, Instant now) {
        User me = users.findById(meId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        Instant last = me.getLastTimelineViewedAt();
        users.updateLastTimelineViewedAt(meId, now);
        if (last == null || last.isAfter(now.minus(properties.highlightAfter()))) {
            return List.of();
        }
        return queries.highlights(meId, last, now, properties.highlightCount());
    }

    /**
     * いいね経由の投稿に「○○さん、ほか N 人がいいねしました」を付ける（BR-50-3、DB 設計書 5.5）。
     * 自分とフォロー中の人の投稿には付けない（いいね経由ではないため）。
     */
    private List<PostResponse> withLikedVia(List<PostResponse> items, long meId, Instant base) {
        List<Long> candidates = items.stream()
                .filter(p -> !p.isMine() && !p.author().followedByMe())
                .map(PostResponse::id)
                .toList();
        // 投稿ごとの、フォロー中の人のいいね（新しい順）
        Map<Long, List<Long>> likersByPost = queries.followeeLikes(meId, candidates, base).stream()
                .collect(Collectors.groupingBy(
                        LikeRow::postId,
                        LinkedHashMap::new,
                        Collectors.mapping(LikeRow::userId, Collectors.toList())));
        List<Long> latestLikerIds = likersByPost.values().stream().map(List::getFirst).distinct().toList();
        Map<Long, User> likers =
                users.findAllById(latestLikerIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return items.stream()
                .map(p -> {
                    List<Long> likerIds = likersByPost.get(p.id());
                    if (likerIds == null) {
                        return p;
                    }
                    User latest = likers.get(likerIds.getFirst());
                    return p.withLikedVia(new PostResponse.LikedVia(
                            latest.getDisplayName(), latest.getUsername(), likerIds.size() - 1));
                })
                .toList();
    }

    /** 投稿の ID の順番どおりに投稿カードを作る。投稿は1回の SQL でまとめて読む。 */
    private List<PostResponse> load(List<Long> postIds, long meId) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> byId =
                posts.findAllById(postIds).stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> ordered = new ArrayList<>();
        for (Long id : postIds) {
            Post post = byId.get(id);
            if (post != null) { // 読み込みの間に消された投稿は飛ばす
                ordered.add(post);
            }
        }
        return views.of(ordered, meId);
    }

    private CursorPage<PostResponse> toPage(List<Post> rows, long meId) {
        return cursors.page(rows, p -> new PostCursor(p.getCreatedAt(), p.getId()), pageRows -> views.of(pageRows, meId));
    }
}
