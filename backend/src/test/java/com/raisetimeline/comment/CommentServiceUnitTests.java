package com.raisetimeline.comment;

import static com.raisetimeline.ApiExceptions.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.post.PostRepository;
import com.raisetimeline.post.TestPosts;
import com.raisetimeline.user.UserSummaries;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/** コメントの業務ルールの単体テスト（BR-35）。DB は偽物にする。 */
@ExtendWith(MockitoExtension.class)
class CommentServiceUnitTests {

    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final long POST_ID = 10L;
    private static final long COMMENT_ID = 20L;

    @Mock
    private CommentRepository comments;

    @Mock
    private PostRepository posts;

    @Mock
    private UserSummaries summaries;

    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(comments, posts, summaries, new Cursors(JsonMapper.builder().build()));
    }

    private static Comment comment(long userId) {
        Comment comment = new Comment(POST_ID, userId, "コメント");
        ReflectionTestUtils.setField(comment, "id", COMMENT_ID);
        return comment;
    }

    /**
     * 削除できる人のデシジョンテーブル（BR-35）。
     *
     * <pre>
     * コメントした本人 | 投稿した本人 | 結果
     *       ○        |      ○       | 削除できる（自分の投稿への自分のコメント）
     *       ○        |      ×       | 削除できる
     *       ×        |      ○       | 削除できる（自分の投稿についた他人のコメント）
     *       ×        |      ×       | FORBIDDEN
     * </pre>
     */
    @ParameterizedTest(name = "コメントした本人={0}・投稿した本人={1} → 削除できる={2}")
    @CsvSource({"true, true, true", "true, false, true", "false, true, true", "false, false, false"})
    @DisplayName("UT-CM-01 コメントを削除できるのは、コメントした本人か投稿した本人")
    void 削除の権限(boolean commentedByMe, boolean postedByMe, boolean deletable) {
        Comment comment = comment(commentedByMe ? ME : OTHER);
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(posts.findById(POST_ID)).thenReturn(Optional.of(TestPosts.entity(POST_ID, postedByMe ? ME : OTHER, "本文")));

        if (deletable) {
            service.delete(ME, COMMENT_ID);
            verify(comments).delete(comment);
        } else {
            assertThat(codeOf(() -> service.delete(ME, COMMENT_ID))).isEqualTo(ErrorCode.FORBIDDEN);
            verify(comments, never()).delete(any());
        }
    }

    @Test
    @DisplayName("UT-CM-01 ないコメントは NOT_FOUND")
    void ないコメント() {
        when(comments.findById(COMMENT_ID)).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.delete(ME, COMMENT_ID))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("UT-CM-01 ない投稿へのコメント・一覧は NOT_FOUND")
    void ない投稿() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.create(ME, POST_ID, new CreateCommentRequest("x"))))
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.list(POST_ID, null, ME))).isEqualTo(ErrorCode.NOT_FOUND);
        verify(comments, never()).save(any());
    }

    @Test
    @DisplayName("UT-CM-01 一覧の deletable は、行ごとに削除の権限と同じ判定になる")
    void 一覧の削除できる印() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(TestPosts.entity(POST_ID, OTHER, "本文")));
        Comment mine = comment(ME);
        Comment others = comment(OTHER);
        when(comments.findByPost(eq(POST_ID), any())).thenReturn(List.of(mine, others));

        List<CommentResponse> items = service.list(POST_ID, null, ME).items();

        assertThat(items).extracting(CommentResponse::deletable).containsExactly(true, false);
    }
}
