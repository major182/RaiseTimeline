package com.raisetimeline.post;

import com.raisetimeline.user.UserSummaries;
import com.raisetimeline.user.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投稿から投稿カード（PostResponse）をまとめて作る（DB 設計書 5.5）。
 * 一覧の 20 件について、投稿者・画像・いいねの数などを、投稿ごとではなく ID の配列でまとめて取る（NF-PE-02）。
 * タイムライン・プロフィール・投稿の詳細のすべてで、この部品を使う。
 */
@Service
public class PostViews {

    private final UserSummaries summaries;

    public PostViews(UserSummaries summaries) {
        this.summaries = summaries;
    }

    /** posts と同じ順番で投稿カードを返す。 */
    @Transactional(readOnly = true)
    public List<PostResponse> of(List<Post> posts, long meId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, UserSummary> authors = summaries.ofIds(authorIds, meId).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        return posts.stream()
                .map(p -> new PostResponse(
                        p.getId(),
                        authors.get(p.getUserId()),
                        p.getBody(),
                        List.of(),
                        p.getCreatedAt(),
                        p.getEditedAt(),
                        0,
                        0,
                        false,
                        null,
                        p.isOwnedBy(meId)))
                .toList();
    }

    public PostResponse of(Post post, long meId) {
        return of(List.of(post), meId).getFirst();
    }
}
