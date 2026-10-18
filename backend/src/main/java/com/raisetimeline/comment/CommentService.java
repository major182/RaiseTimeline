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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** コメント・返信の業務ルール（要件定義書 BR-30・35・37・38、API 設計書 4.5）。 */
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

    /** コメントの一覧（F-CM-02）。古い順。返信は含めない。 */
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

    /** 返信の一覧（F-CM-06）。古い順。 */
    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> listReplies(long commentId, String cursor, long meId) {
        Comment parent = findComment(commentId);
        Post post = findPost(parent.getPostId());
        CommentCursor after = cursors.decode(cursor, CommentCursor.class);
        List<Comment> rows = after == null
                ? comments.findReplies(commentId, PAGE_PLUS_ONE)
                : comments.findRepliesAfter(commentId, after.at(), after.id(), PAGE_PLUS_ONE);
        return cursors.page(
                rows, c -> new CommentCursor(c.getCreatedAt(), c.getId()), pageRows -> toResponses(pageRows, post, meId));
    }

    /** コメント・返信する（F-CM-01、F-CM-05）。 */
    @Transactional
    public CommentResponse create(long meId, long postId, CreateCommentRequest request) {
        Post post = findPost(postId);
        Long parentId = request.parentId() == null ? null : replyTarget(request.parentId(), postId);
        Comment comment;
        try {
            comment = comments.saveAndFlush(new Comment(postId, meId, parentId, request.body()));
        } catch (DataIntegrityViolationException e) {
            // 確かめた直後に、投稿か返信先が消された場合。DB の外部キーが最後の安全網になる（API 設計書 4.5 の 404）
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return toResponses(List.of(comment), post, meId).getFirst();
    }

    /**
     * 返信を記録する先（BR-37）。返信は一段だけなので、返信先が返信なら、その返信先（元のコメント）にする。
     * 返信先がない・別の投稿のコメントなら NOT_FOUND。
     */
    private long replyTarget(long parentId, long postId) {
        Comment parent = findComment(parentId);
        if (parent.getPostId() != postId) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return parent.isReply() ? parent.getParentId() : parent.getId();
    }

    /**
     * コメント・返信を消す（F-CM-04）。消せるのは、書いた本人か、投稿した本人だけ（BR-35）。
     * 本人の確認はサービス層で行う（NF-SE-04）。コメントの返信は DB が一緒に消す（BR-38）。
     */
    @Transactional
    public void delete(long meId, long commentId) {
        Comment comment = findComment(commentId);
        if (!canDelete(comment, findPost(comment.getPostId()), meId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        comments.delete(comment);
    }

    private List<CommentResponse> toResponses(List<Comment> rows, Post post, long meId) {
        List<Long> authorIds = rows.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, UserSummary> authors = summaries.ofIds(authorIds, meId).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        Map<Long, Long> replyCounts = replyCounts(rows);
        return rows.stream()
                .map(c -> new CommentResponse(
                        c.getId(),
                        c.getPostId(),
                        c.getParentId(),
                        authors.get(c.getUserId()),
                        c.getBody(),
                        c.getCreatedAt(),
                        replyCounts.getOrDefault(c.getId(), 0L),
                        canDelete(c, post, meId)))
                .toList();
    }

    /** 返信ではないコメントの、返信の数（返信には返信がつかないので数えない）。 */
    private Map<Long, Long> replyCounts(List<Comment> rows) {
        List<Long> parentIds =
                rows.stream().filter(c -> !c.isReply()).map(Comment::getId).toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return comments.countRepliesByParentIds(parentIds).stream()
                .collect(Collectors.toMap(ReplyCount::parentId, ReplyCount::count));
    }

    private static boolean canDelete(Comment comment, Post post, long meId) {
        return comment.getUserId() == meId || post.isOwnedBy(meId);
    }

    private Comment findComment(long commentId) {
        return comments.findById(commentId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private Post findPost(long postId) {
        return posts.findById(postId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
