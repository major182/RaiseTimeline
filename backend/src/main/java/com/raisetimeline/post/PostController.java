package com.raisetimeline.post;

import com.raisetimeline.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 投稿の API（API 設計書 4.4）。業務ルールは {@link PostService} に置く。 */
@RestController
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 投稿する（F-PO-01）。画像を一緒に送れるよう multipart/form-data で受け取る。
     * フォームの項目は @ModelAttribute で record に入れ、@Valid で入力を確かめる。
     */
    @PostMapping(path = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PostResponse> create(
            @AuthenticationPrincipal AuthenticatedUser me, @Valid @ModelAttribute CreatePostRequest body) {
        PostResponse post = postService.create(me.id(), body);
        return ResponseEntity.created(URI.create("/api/posts/" + post.id())).body(post);
    }

    /** 投稿の詳細（F-PO-04）。 */
    @GetMapping("/api/posts/{postId}")
    PostResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        return postService.get(postId, me.id());
    }

    /** 本文を変える（F-PO-06）。 */
    @PatchMapping("/api/posts/{postId}")
    PostResponse update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @Valid @RequestBody UpdatePostRequest body) {
        return postService.update(me.id(), postId, body);
    }

    /** 投稿を消す（F-PO-03）。 */
    @DeleteMapping("/api/posts/{postId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        postService.delete(me.id(), postId);
        return ResponseEntity.noContent().build();
    }
}
