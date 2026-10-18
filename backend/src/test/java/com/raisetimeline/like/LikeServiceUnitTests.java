package com.raisetimeline.like;

import static com.raisetimeline.ApiExceptions.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.post.PostRepository;
import com.raisetimeline.user.UserSummaries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * いいねの業務ルールの単体テスト（BR-33、A-4）。DB は偽物にする。
 * 何度押しても結果が同じになること自体は DB の操作（重複を無視する INSERT）で決まるので、結合テスト（IT-LK-01）で確かめる。
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceUnitTests {

    private static final long ME = 1L;
    private static final long POST_ID = 10L;

    @Mock
    private LikeRepository likes;

    @Mock
    private PostRepository posts;

    @Mock
    private UserSummaries summaries;

    private LikeService service;

    @BeforeEach
    void setUp() {
        service = new LikeService(likes, posts, summaries, new Cursors(JsonMapper.builder().build()));
    }

    @Test
    @DisplayName("UT-LK-01 いいねすると、重複を無視して追加し、操作のあとの状態（いいね済み・数）を返す")
    void いいねする() {
        when(posts.existsById(POST_ID)).thenReturn(true);
        when(likes.countByPostId(POST_ID)).thenReturn(3L);

        LikeResponse response = service.like(ME, POST_ID);

        verify(likes).insertIfAbsent(POST_ID, ME);
        assertThat(response).isEqualTo(new LikeResponse(true, 3L));
    }

    @Test
    @DisplayName("UT-LK-01 取り消すと、いいねがなくてもエラーにせず、操作のあとの状態（未いいね・数）を返す")
    void 取り消す() {
        when(posts.existsById(POST_ID)).thenReturn(true);
        when(likes.countByPostId(POST_ID)).thenReturn(0L);

        LikeResponse response = service.unlike(ME, POST_ID);

        verify(likes).deleteByPair(POST_ID, ME);
        assertThat(response).isEqualTo(new LikeResponse(false, 0L));
    }

    @Test
    @DisplayName("UT-LK-01 ない投稿へのいいね・取り消し・一覧は NOT_FOUND で、DB を変えない")
    void ない投稿() {
        when(posts.existsById(POST_ID)).thenReturn(false);

        assertThat(codeOf(() -> service.like(ME, POST_ID))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.unlike(ME, POST_ID))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.likers(POST_ID, null, ME))).isEqualTo(ErrorCode.NOT_FOUND);
        verify(likes, never()).insertIfAbsent(anyLong(), anyLong());
        verify(likes, never()).deleteByPair(anyLong(), anyLong());
    }
}
