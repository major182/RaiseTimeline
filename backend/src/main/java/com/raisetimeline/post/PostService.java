package com.raisetimeline.post;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 投稿の業務ルール（要件定義書 BR-10〜16、API 設計書 4.4）。 */
@Service
public class PostService {

    private final PostRepository posts;
    private final PostViews views;

    public PostService(PostRepository posts, PostViews views) {
        this.posts = posts;
        this.views = views;
    }

    /** 投稿する（F-PO-01）。本文も画像もなければ POST_EMPTY（BR-11）。空白だけの本文は空とみなす。 */
    @Transactional
    public PostResponse create(long meId, CreatePostRequest request) {
        String body = normalize(request.body());
        if (body.isEmpty()) {
            throw new ApiException(ErrorCode.POST_EMPTY);
        }
        Post post = posts.save(new Post(meId, body));
        return views.of(post, meId);
    }

    /** 投稿の詳細（F-PO-04）。 */
    @Transactional(readOnly = true)
    public PostResponse get(long postId, long meId) {
        return views.of(find(postId), meId);
    }

    /**
     * 本文を変える（F-PO-06）。本人の投稿でなければ 403（BR-12。サービス層で確かめる。NF-SE-04）。
     * 画像のない投稿で本文を空にしたら POST_EMPTY（BR-11）。
     */
    @Transactional
    public PostResponse update(long meId, long postId, UpdatePostRequest request) {
        Post post = findOwned(postId, meId);
        String body = normalize(request.body());
        if (body.isEmpty()) {
            throw new ApiException(ErrorCode.POST_EMPTY);
        }
        post.edit(body, Instant.now());
        posts.flush(); // 更新日時（@PreUpdate）を入れてから返す
        return views.of(post, meId);
    }

    /** 投稿を消す（F-PO-03）。コメント・いいね・画像も DB の ON DELETE CASCADE で消える（BR-13）。 */
    @Transactional
    public void delete(long meId, long postId) {
        posts.delete(findOwned(postId, meId));
    }

    private Post find(long postId) {
        return posts.findById(postId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** 投稿を探し、本人のものか確かめる。投稿は全員に公開されているので、他人の投稿は 404 ではなく 403（API 設計書 2.3）。 */
    private Post findOwned(long postId, long meId) {
        Post post = find(postId);
        if (!post.isOwnedBy(meId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return post;
    }

    /** 空白だけの本文は空にする（見た目に何もない投稿を作らない。BR-11）。 */
    private static String normalize(String body) {
        return body == null || body.isBlank() ? "" : body;
    }
}
