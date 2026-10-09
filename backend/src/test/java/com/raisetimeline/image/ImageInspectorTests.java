package com.raisetimeline.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.image.ImageInspector.ImageInfo;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 画像の形式の判定と、幅・高さの読み取り（BR-21）。DB を使わない単体テスト。 */
class ImageInspectorTests {

    @Test
    void PNGを判定し幅と高さを読む() {
        assertThat(ImageInspector.inspect(TestImages.png(640, 480))).isEqualTo(new ImageInfo(ImageType.PNG, 640, 480));
    }

    @Test
    void JPEGを判定し幅と高さを読む() {
        assertThat(ImageInspector.inspect(TestImages.jpeg(1200, 800)))
                .isEqualTo(new ImageInfo(ImageType.JPEG, 1200, 800));
    }

    @Test
    void GIFを判定し幅と高さを読む() {
        assertThat(ImageInspector.inspect(TestImages.gif(32, 16))).isEqualTo(new ImageInfo(ImageType.GIF, 32, 16));
    }

    @Test
    void WebPの3つの書き方をどれも判定する() {
        assertThat(ImageInspector.inspect(TestImages.webpLossy(300, 200)))
                .isEqualTo(new ImageInfo(ImageType.WEBP, 300, 200));
        assertThat(ImageInspector.inspect(TestImages.webpLossless(1, 16384)))
                .isEqualTo(new ImageInfo(ImageType.WEBP, 1, 16384));
        assertThat(ImageInspector.inspect(TestImages.webpExtended(5000, 3000)))
                .isEqualTo(new ImageInfo(ImageType.WEBP, 5000, 3000));
    }

    @Test
    void 画像でない中身は415() {
        assertInvalid("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8));
        assertInvalid("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        assertInvalid("BM".getBytes(StandardCharsets.US_ASCII)); // BMP は受け付けない
        assertInvalid(new byte[0]);
    }

    @Test
    void 途中で切れた画像は415() {
        byte[] png = TestImages.png(10, 10);
        assertInvalid(Arrays.copyOf(png, 12)); // 幅・高さの前で切れている
        byte[] jpeg = TestImages.jpeg(10, 10);
        assertInvalid(Arrays.copyOf(jpeg, 4));
        assertInvalid(Arrays.copyOf(TestImages.webpLossy(10, 10), 22));
    }

    @Test
    void 幅か高さが0の画像は415() {
        assertInvalid(TestImages.webpLossy(0, 10));
    }

    @Test
    void WebPの見出しが壊れていれば415() {
        byte[] data = TestImages.webpLossy(10, 10);
        data[23] = 0; // 開始の印を壊す
        assertInvalid(data);
        byte[] lossless = TestImages.webpLossless(10, 10);
        lossless[20] = 0; // 印を壊す
        assertInvalid(lossless);
    }

    private static void assertInvalid(byte[] data) {
        assertThatThrownBy(() -> ImageInspector.inspect(data))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.IMAGE_TYPE_INVALID));
    }
}
