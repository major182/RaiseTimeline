package com.raisetimeline.user;

import static com.raisetimeline.ApiExceptions.codeOf;
import static com.raisetimeline.ApiExceptions.fieldCodesOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.common.pagination.Cursors;
import com.raisetimeline.follow.FollowRepository;
import com.raisetimeline.image.ImageInspector.ImageInfo;
import com.raisetimeline.image.ImageType;
import com.raisetimeline.image.ImageUploads;
import com.raisetimeline.image.ImageUploads.CheckedImage;
import com.raisetimeline.image.ImageUploads.StoredImage;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/** 利用者・プロフィールの業務ルールの単体テスト（BR-02・24、F-US-03・04）。DB と画像の保存は偽物にする。 */
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTests {

    private static final long ME = 1L;

    @Mock
    private UserRepository users;

    @Mock
    private FollowRepository follows;

    @Mock
    private UserSummaries summaries;

    @Mock
    private ImageUploads uploads;

    private UserService service;
    private User me;

    @BeforeEach
    void setUp() {
        service = new UserService(users, follows, summaries, new Cursors(JsonMapper.builder().build()), uploads);
        me = user(ME, "me_user");
    }

    private static User user(long id, String username) {
        User user = User.register(username, username + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** プロフィールの応答を作るための、利用者の要約を用意する。 */
    private void 要約を返す() {
        when(summaries.of(any(User.class), eq(ME)))
                .thenReturn(new UserSummary(ME, "me_user", "me_user", null, "", false, true));
    }

    // --- プロフィールの編集（BR-02） ---

    @Test
    @DisplayName("UT-US-01 ほかの人が使っているユーザー名には変えられない（USERNAME_TAKEN）")
    void 使われているユーザー名() {
        when(users.findById(ME)).thenReturn(Optional.of(me));
        when(users.existsByUsernameExcept("Taken_Name", ME)).thenReturn(true);

        assertThat(fieldCodesOf(() -> service.updateProfile(ME, new UpdateProfileRequest(null, "Taken_Name", null))))
                .containsExactly("USERNAME_TAKEN");
        assertThat(me.getUsername()).isEqualTo("me_user");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UT-US-01 ユーザー名を送らなければ、重複を確かめずに表示名などだけを変える")
    void ユーザー名を送らない() {
        when(users.findById(ME)).thenReturn(Optional.of(me));
        要約を返す();

        service.updateProfile(ME, new UpdateProfileRequest("新しい名前", null, "よろしく"));

        verify(users, never()).existsByUsernameExcept(anyString(), anyLong());
        assertThat(me.getDisplayName()).isEqualTo("新しい名前");
        assertThat(me.getBio()).isEqualTo("よろしく");
    }

    @Test
    @DisplayName("UT-US-01 確かめた直後に別の人が同じ名前にして DB の制約に当たっても USERNAME_TAKEN")
    void 同時の変更() {
        when(users.findById(ME)).thenReturn(Optional.of(me));
        when(users.saveAndFlush(me)).thenThrow(new DataIntegrityViolationException("users_username_lower_key"));

        assertThat(fieldCodesOf(() -> service.updateProfile(ME, new UpdateProfileRequest(null, "new_name", null))))
                .containsExactly("USERNAME_TAKEN");
    }

    // --- 検索語の長さ（API 設計書 2.4 の QUERY_LENGTH。境界値） ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("UT-US-03 検索語が空（空白だけ・送られない）なら QUERY_LENGTH")
    void 検索語が空(String q) {
        assertThat(fieldCodesOf(() -> service.search(q, null, ME))).containsExactly("QUERY_LENGTH");
        verify(users, never()).search(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("UT-US-03 検索語が 51 文字なら QUERY_LENGTH")
    void 検索語が長すぎる() {
        assertThat(fieldCodesOf(() -> service.search("あ".repeat(51), null, ME))).containsExactly("QUERY_LENGTH");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    @DisplayName("UT-US-03 検索語が 1〜50 文字なら探す（絵文字も 1 文字と数える）")
    void 検索語の範囲内(int length) {
        service.search("😀".repeat(length), null, ME);

        verify(users).search(anyString(), eq("😀".repeat(length)), eq(Cursors.PAGE_SIZE + 1), eq(0));
    }

    @Test
    @DisplayName("UT-US-03 検索語の %・_・\\ は、ただの文字として探す")
    void 特別な記号() {
        assertThat(UserService.escapeLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }

    // --- おすすめ（F-US-04。境界値） ---

    @Test
    @DisplayName("UT-US-04 フォロー中の人がフォローしている人で 5 人そろえば、ほかの人で埋めない")
    void 五人そろう() {
        List<User> five = LongStream.rangeClosed(2, 6).mapToObj(id -> user(id, "user_" + id)).toList();
        when(users.findFollowedByFollowing(ME, 5)).thenReturn(five);

        service.recommendations(ME);

        verify(users, never()).findRecentExcept(anyLong(), any(), anyInt());
        verify(summaries).of(five, ME);
    }

    @Test
    @DisplayName("UT-US-04 2 人しかいなければ、選んだ人を除いて、最近登録した人で残りの 3 人を埋める")
    void 足りなければ埋める() {
        when(users.findFollowedByFollowing(ME, 5)).thenReturn(List.of(user(2, "user_2"), user(3, "user_3")));

        service.recommendations(ME);

        verify(users).findRecentExcept(ME, List.of(2L, 3L, -1L), 3);
    }

    // --- アイコン（BR-24） ---

    @Test
    @DisplayName("UT-US-02 アイコンのファイルがなければ REQUIRED")
    void ファイルがない() {
        assertThat(codeOf(() -> service.changeAvatar(ME, null))).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(fieldCodesOf(() -> service.changeAvatar(ME, new MockMultipartFile("file", new byte[0]))))
                .containsExactly("REQUIRED");
    }

    @Test
    @DisplayName("UT-US-02 アイコンを変えたら、前のアイコンのファイルは DB の確定後に消すよう頼む。前がなければ何も消さない")
    void 前のアイコンを消す() {
        when(users.findById(ME)).thenReturn(Optional.of(me));
        要約を返す();
        CheckedImage checked = new CheckedImage(new byte[] {1}, new ImageInfo(ImageType.PNG, 10, 10));
        when(uploads.check(any())).thenReturn(checked);
        when(uploads.store(eq(checked), any()))
                .thenReturn(new StoredImage("avatars/1.png", checked.info(), 1))
                .thenReturn(new StoredImage("avatars/2.png", checked.info(), 1));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});

        service.changeAvatar(ME, file);
        verify(uploads, never()).deleteAfterCommit(any());

        service.changeAvatar(ME, file);
        verify(uploads).deleteAfterCommit(List.of("avatars/1.png"));
        assertThat(me.getAvatarKey()).isEqualTo("avatars/2.png");
    }
}
