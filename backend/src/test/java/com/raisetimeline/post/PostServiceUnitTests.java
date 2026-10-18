package com.raisetimeline.post;

import static com.raisetimeline.ApiExceptions.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.image.ImageInspector.ImageInfo;
import com.raisetimeline.image.ImageType;
import com.raisetimeline.image.ImageUploads;
import com.raisetimeline.image.ImageUploads.CheckedImage;
import com.raisetimeline.image.ImageUploads.StoredImage;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** 投稿の業務ルールの単体テスト（BR-11・12・15・20）。DB と画像の保存は偽物にする。 */
@ExtendWith(MockitoExtension.class)
class PostServiceUnitTests {

    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final long POST_ID = 10L;

    @Mock
    private PostRepository posts;

    @Mock
    private PostImageRepository images;

    @Mock
    private ImageUploads uploads;

    @Mock
    private PostViews views;

    @InjectMocks
    private PostService service;

    private static List<MultipartFile> files(int count) {
        return IntStream.range(0, count)
                .<MultipartFile>mapToObj(i -> new MockMultipartFile("images", "a.png", "image/png", new byte[] {1}))
                .toList();
    }

    /** 保存すると DB が ID を決める動きを真似る。 */
    private void 保存すると番号が付く() {
        when(posts.save(any())).thenAnswer(i -> {
            Post post = i.getArgument(0);
            return TestPosts.entity(POST_ID, post.getUserId(), post.getBody());
        });
    }

    private void 画像は正しい() {
        CheckedImage checked = new CheckedImage(new byte[] {1}, new ImageInfo(ImageType.PNG, 10, 10));
        when(uploads.check(any())).thenReturn(checked);
        when(uploads.store(eq(checked), any())).thenReturn(new StoredImage("posts/a.png", checked.info(), 1));
    }

    // --- 本文と画像の組み合わせ（BR-11。デシジョンテーブル） ---

    @ParameterizedTest(name = "本文「{0}」・画像 {1} 枚 → 投稿できる")
    @CsvSource({"こんにちは, 0", "こんにちは, 1", "'', 1", "'   ', 1"})
    @DisplayName("UT-PO-02 本文か画像のどちらかがあれば投稿できる")
    void どちらかがある(String body, int imageCount) {
        保存すると番号が付く();
        if (imageCount > 0) {
            画像は正しい();
        }

        service.create(ME, new CreatePostRequest(body, files(imageCount)));

        verify(posts).save(any());
        verify(images, times(imageCount)).save(any());
    }

    @ParameterizedTest(name = "本文「{0}」・画像なし → POST_EMPTY")
    @CsvSource(value = {"''", "'   '", "NULL"}, nullValues = "NULL")
    @DisplayName("UT-PO-02 本文が空（空白だけ・送られない）で画像もなければ POST_EMPTY")
    void 両方ない(String body) {
        assertThat(codeOf(() -> service.create(ME, new CreatePostRequest(body, null))))
                .isEqualTo(ErrorCode.POST_EMPTY);
        verify(posts, never()).save(any());
    }

    @Test
    @DisplayName("UT-PO-02 空のファイル欄は画像として数えない")
    void 空のファイル欄() {
        MultipartFile empty = new MockMultipartFile("images", new byte[0]);

        assertThat(codeOf(() -> service.create(ME, new CreatePostRequest("", List.of(empty)))))
                .isEqualTo(ErrorCode.POST_EMPTY);
    }

    // --- 画像の枚数（BR-20。境界値） ---

    @Test
    @DisplayName("UT-PO-03 画像は 4 枚までなら投稿でき、送った順に 1〜4 の並び順で保存する")
    void 四枚まで() {
        保存すると番号が付く();
        画像は正しい();

        service.create(ME, new CreatePostRequest("", files(4)));

        verify(images, times(4)).save(any());
    }

    @Test
    @DisplayName("UT-PO-03 画像が 5 枚なら IMAGE_TOO_MANY で、画像の確かめも保存もしない")
    void 五枚は多い() {
        assertThat(codeOf(() -> service.create(ME, new CreatePostRequest("本文", files(5)))))
                .isEqualTo(ErrorCode.IMAGE_TOO_MANY);
        verify(uploads, never()).check(any());
        verify(posts, never()).save(any());
    }

    @Test
    @DisplayName("UT-PO-06 画像が 1 枚でも正しくなければ、投稿も画像も保存しない")
    void 一枚でも誤りがあれば保存しない() {
        CheckedImage checked = new CheckedImage(new byte[] {1}, new ImageInfo(ImageType.PNG, 10, 10));
        when(uploads.check(any()))
                .thenReturn(checked)
                .thenThrow(new ApiException(ErrorCode.IMAGE_TYPE_INVALID));

        assertThat(codeOf(() -> service.create(ME, new CreatePostRequest("", files(2)))))
                .isEqualTo(ErrorCode.IMAGE_TYPE_INVALID);
        verify(uploads, never()).store(any(), any());
        verify(posts, never()).save(any());
    }

    // --- 編集・削除の権限（BR-12、NF-SE-04） ---

    @Test
    @DisplayName("UT-PO-05 他人の投稿は編集できない（FORBIDDEN）")
    void 他人の投稿の編集() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(TestPosts.entity(POST_ID, OTHER, "元の本文")));

        assertThat(codeOf(() -> service.update(ME, POST_ID, new UpdatePostRequest("書き換え"))))
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("UT-PO-05 他人の投稿は削除できない（FORBIDDEN）")
    void 他人の投稿の削除() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(TestPosts.entity(POST_ID, OTHER, "元の本文")));

        assertThat(codeOf(() -> service.delete(ME, POST_ID))).isEqualTo(ErrorCode.FORBIDDEN);
        verify(posts, never()).delete(any());
    }

    @Test
    @DisplayName("UT-PO-05 ない投稿は NOT_FOUND")
    void ない投稿() {
        when(posts.findById(POST_ID)).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.get(POST_ID, ME))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.update(ME, POST_ID, new UpdatePostRequest("x"))))
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(codeOf(() -> service.delete(ME, POST_ID))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("UT-PO-05 本人の投稿は編集でき、「編集済み」の時刻が入る")
    void 本人の投稿の編集() {
        Post post = TestPosts.entity(POST_ID, ME, "元の本文");
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));

        service.update(ME, POST_ID, new UpdatePostRequest("書き換え"));

        assertThat(post.getBody()).isEqualTo("書き換え");
        assertThat(post.getEditedAt()).isNotNull();
    }

    @Test
    @DisplayName("UT-PO-05 本人の投稿を削除すると、画像のファイルは DB の確定後に消すよう頼む")
    void 本人の投稿の削除() {
        Post post = TestPosts.entity(POST_ID, ME, "本文");
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(images.findKeysByPostId(POST_ID)).thenReturn(List.of("posts/a.png"));

        service.delete(ME, POST_ID);

        verify(uploads).deleteAfterCommit(List.of("posts/a.png"));
        verify(posts).delete(post);
    }

    // --- 編集で本文を空にする（BR-11・BR-15） ---

    @Test
    @DisplayName("UT-PO-02 画像のない投稿で本文を空にしたら POST_EMPTY")
    void 画像なしで空にする() {
        when(posts.findById(POST_ID)).thenReturn(Optional.of(TestPosts.entity(POST_ID, ME, "本文")));
        when(images.existsByPostId(POST_ID)).thenReturn(false);

        assertThat(codeOf(() -> service.update(ME, POST_ID, new UpdatePostRequest(" "))))
                .isEqualTo(ErrorCode.POST_EMPTY);
    }

    @Test
    @DisplayName("UT-PO-02 画像のある投稿なら、本文を空にできる")
    void 画像ありで空にする() {
        Post post = TestPosts.entity(POST_ID, ME, "本文");
        when(posts.findById(POST_ID)).thenReturn(Optional.of(post));
        when(images.existsByPostId(POST_ID)).thenReturn(true);

        service.update(ME, POST_ID, new UpdatePostRequest(""));

        assertThat(post.getBody()).isEmpty();
    }
}
