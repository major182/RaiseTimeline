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

/** コメントの API（API 設計書 4.5）。業務ルールは {@link CommentService} に置く。 */
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** コメントの一覧（F-CM-02）。古い順。 */
    @GetMapping("/api/posts/{postId}/comments")
    CursorPage<CommentResponse> list(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @RequestParam(required = false) String cursor) {
        return commentService.list(postId, cursor, me.id());
    }

    /** コメントする（F-CM-01）。 */
    @PostMapping("/api/posts/{postId}/comments")
    ResponseEntity<CommentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long postId,
            @Valid @RequestBody CreateCommentRequest body) {
        CommentResponse comment = commentService.create(me.id(), postId, body);
        return ResponseEntity.created(URI.create("/api/comments/" + comment.id())).body(comment);
    }

    /** コメントを消す（F-CM-04）。 */
    @DeleteMapping("/api/comments/{commentId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable long commentId) {
        commentService.delete(me.id(), commentId);
        return ResponseEntity.noContent().build();
    }
}
