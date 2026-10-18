package com.raisetimeline.follow;

import static com.raisetimeline.ApiExceptions.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.user.UserRepository;
import com.raisetimeline.user.UserSummaries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/** フォローの業務ルールの単体テスト（BR-42・43）。DB は偽物にする。相手を同値分割（自分・他人・いない人）で選ぶ。 */
@ExtendWith(MockitoExtension.class)
class FollowServiceUnitTests {

    private static final long ME = 1L;
    private static final long OTHER = 2L;

    @Mock
    private FollowRepository follows;

    @Mock
    private UserRepository users;

    @Mock
    private UserSummaries summaries;

    private FollowService service;

    @BeforeEach
    void setUp() {
        service = new FollowService(follows, users, summaries, new Cursors(JsonMapper.builder().build()));
    }

    @Test
    @DisplayName("UT-FL-01 自分自身はフォローできない（VALIDATION_FAILED）。利用者を探しもしない")
    void 自分自身() {
        assertThat(codeOf(() -> service.follow(ME, ME))).isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(users, follows);
    }

    @Test
    @DisplayName("UT-FL-01 他人はフォローできる（フォロー済みでも重複を無視して追加するので結果は同じ）")
    void 他人() {
        when(users.existsById(OTHER)).thenReturn(true);

        service.follow(ME, OTHER);

        verify(follows).insertIfAbsent(ME, OTHER);
    }

    @Test
    @DisplayName("UT-FL-01 いない利用者のフォロー・解除・一覧は NOT_FOUND で、DB を変えない")
    void いない利用者() {
        when(users.existsById(OTHER)).thenReturn(false);

        assertThat(codeOf(() -> service.follow(ME, OTHER))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.unfollow(ME, OTHER))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.following(OTHER, null, ME))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.followers(OTHER, null, ME))).isEqualTo(ErrorCode.NOT_FOUND);
        verify(follows, never()).insertIfAbsent(anyLong(), anyLong());
        verify(follows, never()).deleteByPair(anyLong(), anyLong());
    }

    @Test
    @DisplayName("UT-FL-01 フォローをやめると、フォローしていなくてもエラーにしない")
    void やめる() {
        when(users.existsById(OTHER)).thenReturn(true);

        service.unfollow(ME, OTHER);

        verify(follows).deleteByPair(ME, OTHER);
    }
}
