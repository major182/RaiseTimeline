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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.raisetimeline.common.error.ApiErrors;
import com.raisetimeline.common.error.ErrorCode;

/** 投稿の API（API 設計書 4.4）。業務ルールは {@link PostService} に置く。 */
@RestController
@Tag(name = "投稿", description = "API 設計書 4.4")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 投稿する（F-PO-01）。画像を一緒に送れるよう multipart/form-data で受け取る。
     * フォームの項目は @ModelAttribute で record に入れ、@Valid で入力を確かめる。
     */
    @Operation(
            summary = "投稿する",
            description = "本文（0〜280 文字）と画像（0〜4 枚、1 枚 5MB まで。送った順が並び順）。本文も画像もなければ POST_EMPTY（BR-11）")
    @ApiResponse(responseCode = "201", description = "投稿した", useReturnTypeSchema = true)
    @ApiErrors({
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.POST_EMPTY,
        ErrorCode.IMAGE_TOO_MANY,
        ErrorCode.IMAGE_TOO_LARGE,
        ErrorCode.IMAGE_TYPE_INVALID
    })
    @PostMapping(path = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PostResponse> create(
            @AuthenticationPrincipal AuthenticatedUser me, @Valid @ModelAttribute CreatePostRequest body) {
        PostResponse post = postService.create(me.id(), body);
        return ResponseEntity.created(URI.create("/api/posts/" + post.id())).body(post);
    }

    /** 投稿の詳細（F-PO-04）。 */
    @Operation(summary = "投稿の詳細", description = "likedVia は常に null")
    @ApiErrors(ErrorCode.NOT_FOUND)
    @GetMapping("/api/posts/{postId}")
    PostResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        return postService.get(postId, me.id());
    }

    /** 本文を変える（F-PO-06）。 */
    @Operation(
            summary = "投稿の本文を変える",
            description = "変えられるのは本文だけ（BR-15）。editedAt を今にする。画像のない投稿で本文を空にしたら POST_EMPTY")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.POST_EMPTY, ErrorCode.FORBIDDEN, ErrorCode.NOT_FOUND})
    @PatchMapping("/api/posts/{postId}")
    PostResponse update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @Valid @RequestBody UpdatePostRequest body) {
        return postService.update(me.id(), postId, body);
    }

    /** 投稿を消す（F-PO-03）。 */
    @Operation(summary = "投稿を消す", description = "コメント・いいね・画像も消える（BR-13）")
    @ApiResponse(responseCode = "204", description = "消した")
    @ApiErrors({ErrorCode.FORBIDDEN, ErrorCode.NOT_FOUND})
    @DeleteMapping("/api/posts/{postId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long postId) {
        postService.delete(me.id(), postId);
        return ResponseEntity.noContent().build();
    }
}
