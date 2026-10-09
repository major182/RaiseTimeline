package com.raisetimeline.comment;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.post.Post;
import com.raisetimeline.post.PostRepository;
import com.raisetimeline.user.UserSummaries;
import com.raisetimeline.user.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** コメントの業務ルール（要件定義書 BR-30・35、API 設計書 4.5）。 */
@Service
public class CommentService {

    /** 続きがあるかを知るため、1 件多く取る（{@link Cursors#page}）。 */
    private static final Limit PAGE_PLUS_ONE = Limit.of(Cursors.PAGE_SIZE + 1);

    private final CommentRepository comments;
    private final PostRepository posts;
    private final UserSummaries summaries;
    private final Cursors cursors;

    public CommentService(
            CommentRepository comments, PostRepository posts, UserSummaries summaries, Cursors cursors) {
        this.comments = comments;
        this.posts = posts;
        this.summaries = summaries;
        this.cursors = cursors;
    }

    /** コメントの一覧（F-CM-02）。古い順。 */
    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> list(long postId, String cursor, long meId) {
        Post post = findPost(postId);
        CommentCursor after = cursors.decode(cursor, CommentCursor.class);
        List<Comment> rows = after == null
                ? comments.findByPost(postId, PAGE_PLUS_ONE)
                : comments.findByPostAfter(postId, after.at(), after.id(), PAGE_PLUS_ONE);
        return cursors.page(
                rows, c -> new CommentCursor(c.getCreatedAt(), c.getId()), pageRows -> toResponses(pageRows, post, meId));
    }

    /** コメントする（F-CM-01）。 */
    @Transactional
    public CommentResponse create(long meId, long postId, CreateCommentRequest request) {
        Post post = findPost(postId);
        Comment comment = comments.save(new Comment(postId, meId, request.body()));
        return toResponses(List.of(comment), post, meId).getFirst();
    }

    /**
     * コメントを消す（F-CM-04）。消せるのは、コメントした本人か、投稿した本人だけ（BR-35）。
     * 本人の確認はサービス層で行う（NF-SE-04）。
     */
    @Transactional
    public void delete(long meId, long commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!canDelete(comment, findPost(comment.getPostId()), meId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        comments.delete(comment);
    }

    private List<CommentResponse> toResponses(List<Comment> rows, Post post, long meId) {
        List<Long> authorIds = rows.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, UserSummary> authors = summaries.ofIds(authorIds, meId).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        return rows.stream()
                .map(c -> new CommentResponse(
                        c.getId(),
                        c.getPostId(),
                        authors.get(c.getUserId()),
                        c.getBody(),
                        c.getCreatedAt(),
                        canDelete(c, post, meId)))
                .toList();
    }

    private static boolean canDelete(Comment comment, Post post, long meId) {
        return comment.getUserId() == meId || post.isOwnedBy(meId);
    }

    private Post findPost(long postId) {
        return posts.findById(postId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
