package com.raisetimeline.post;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.image.ImageKeys;
import com.raisetimeline.image.ImageUploads;
import com.raisetimeline.image.ImageUploads.CheckedImage;
import com.raisetimeline.image.ImageUploads.StoredImage;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 投稿の業務ルール（要件定義書 BR-10〜16・20〜22、API 設計書 4.4）。 */
@Service
public class PostService {

    private final PostRepository posts;
    private final PostImageRepository images;
    private final ImageUploads uploads;
    private final PostViews views;

    public PostService(PostRepository posts, PostImageRepository images, ImageUploads uploads, PostViews views) {
        this.posts = posts;
        this.images = images;
        this.uploads = uploads;
        this.views = views;
    }

    /**
     * 投稿する（F-PO-01・02）。本文も画像もなければ POST_EMPTY（BR-11）。空白だけの本文は空とみなす。
     * 「画像をすべて確かめる → 保存先に保存 → DB に保存」の順。DB の保存が取り消されたら、保存したファイルを消す（API 設計書 4.4）。
     */
    @Transactional
    public PostResponse create(long meId, CreatePostRequest request) {
        String body = normalize(request.body());
        List<MultipartFile> files = request.imageFiles();
        if (files.size() > PostImage.MAX_PER_POST) {
            throw new ApiException(ErrorCode.IMAGE_TOO_MANY);
        }
        if (body.isEmpty() && files.isEmpty()) {
            throw new ApiException(ErrorCode.POST_EMPTY);
        }
        // 1 枚でも誤りがあれば何も保存しないよう、保存の前に全部を確かめる
        List<CheckedImage> checked = files.stream().map(uploads::check).toList();
        Post post = posts.save(new Post(meId, body));
        Instant now = Instant.now();
        for (int i = 0; i < checked.size(); i++) {
            StoredImage stored = uploads.store(checked.get(i), type -> ImageKeys.post(type, now));
            images.save(new PostImage(
                    post.getId(),
                    i + 1,
                    stored.key(),
                    stored.info().type().contentType(),
                    stored.sizeBytes(),
                    stored.info().width(),
                    stored.info().height()));
        }
        images.flush();
        return views.of(post, meId);
    }

    /** 投稿の詳細（F-PO-04）。 */
    @Transactional(readOnly = true)
    public PostResponse get(long postId, long meId) {
        return views.of(find(postId), meId);
    }

    /**
     * 本文を変える（F-PO-06）。本人の投稿でなければ 403（BR-12。サービス層で確かめる。NF-SE-04）。
     * 画像のない投稿で本文を空にしたら POST_EMPTY（BR-11）。画像は変えられない（BR-15）。
     */
    @Transactional
    public PostResponse update(long meId, long postId, UpdatePostRequest request) {
        Post post = findOwned(postId, meId);
        String body = normalize(request.body());
        if (body.isEmpty() && !images.existsByPostId(postId)) {
            throw new ApiException(ErrorCode.POST_EMPTY);
        }
        post.edit(body, Instant.now());
        posts.flush(); // 更新日時（@PreUpdate）を入れてから返す
        return views.of(post, meId);
    }

    /**
     * 投稿を消す（F-PO-03）。コメント・いいね・画像の行も DB の ON DELETE CASCADE で消える（BR-13）。
     * 画像のファイルは、DB の削除が確定してから消す（DB 設計書 4.3）。
     */
    @Transactional
    public void delete(long meId, long postId) {
        Post post = findOwned(postId, meId);
        uploads.deleteAfterCommit(images.findKeysByPostId(postId));
        posts.delete(post);
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
