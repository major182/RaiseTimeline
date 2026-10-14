package com.raisetimeline.comment;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.pagination.CursorPage;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.raisetimeline.common.error.ApiErrors;
import com.raisetimeline.common.error.ErrorCode;

/** コメントの API（API 設計書 4.5）。業務ルールは {@link CommentService} に置く。 */
@RestController
@Tag(name = "コメント", description = "API 設計書 4.5")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** コメントの一覧（F-CM-02）。古い順。 */
    @Operation(summary = "コメントの一覧", description = "古い順。20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.NOT_FOUND})
    @GetMapping("/api/posts/{postId}/comments")
    CursorPage<CommentResponse> list(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @RequestParam(required = false) String cursor) {
        return commentService.list(postId, cursor, me.id());
    }

    /** コメントする（F-CM-01）。 */
    @Operation(summary = "コメントする", description = "本文は 1〜280 文字（BR-30）")
    @ApiResponse(responseCode = "201", description = "コメントした", useReturnTypeSchema = true)
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.NOT_FOUND})
    @PostMapping("/api/posts/{postId}/comments")
    ResponseEntity<CommentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @Valid @RequestBody CreateCommentRequest body) {
        CommentResponse comment = commentService.create(me.id(), postId, body);
        return ResponseEntity.created(URI.create("/api/comments/" + comment.id())).body(comment);
    }

    /** コメントを消す（F-CM-04）。 */
    @Operation(summary = "コメントを消す", description = "消せるのはコメントした本人と、投稿した本人（BR-35）")
    @ApiResponse(responseCode = "204", description = "消した")
    @ApiErrors({ErrorCode.FORBIDDEN, ErrorCode.NOT_FOUND})
    @DeleteMapping("/api/comments/{commentId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long commentId) {
        commentService.delete(me.id(), commentId);
        return ResponseEntity.noContent().build();
    }
}
