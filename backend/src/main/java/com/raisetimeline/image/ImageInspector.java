package com.raisetimeline.image;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 画像の形式を中身（先頭のバイト）で判定し、幅と高さを読む（BR-21）。
 *
 * <p>拡張子や、ブラウザが送る Content-Type は書き換えられるので信じない。
 * 画像全体は読み込まず（画素を展開しない）、各形式の決まった位置にある幅・高さだけを読む。
 * Java の標準（ImageIO）は WebP を読めないため、4 つの形式とも同じやり方で自分で読む。
 * 形式が違う・壊れているときは 415（IMAGE_TYPE_INVALID）。
 */
public final class ImageInspector {

    /** 形式と大きさ（ピクセル）。 */
    public record ImageInfo(ImageType type, int width, int height) {}

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private ImageInspector() {}

    public static ImageInfo inspect(byte[] data) {
        try {
            ImageInfo info = read(data);
            if (info == null || info.width() < 1 || info.height() < 1) {
                throw invalid();
            }
            return info;
        } catch (IndexOutOfBoundsException e) {
            throw invalid(); // 途中で切れている（壊れた）ファイル
        }
    }

    private static ImageInfo read(byte[] d) {
        if (startsWith(d, PNG_SIGNATURE)) {
            // 8 バイトの印のあと、最初の IHDR に幅・高さ（4 バイトずつ、大きい桁から）
            return new ImageInfo(ImageType.PNG, int32be(d, 16), int32be(d, 20));
        }
        if (startsWith(d, ascii("GIF87a")) || startsWith(d, ascii("GIF89a"))) {
            // 幅・高さ（2 バイトずつ、小さい桁から）
            return new ImageInfo(ImageType.GIF, uint16le(d, 6), uint16le(d, 8));
        }
        if (d.length >= 3 && u8(d, 0) == 0xFF && u8(d, 1) == 0xD8 && u8(d, 2) == 0xFF) {
            return readJpeg(d);
        }
        if (startsWith(d, ascii("RIFF")) && Arrays.equals(d, 8, 12, ascii("WEBP"), 0, 4)) {
            return readWebp(d);
        }
        return null;
    }

    /** JPEG：区切り（マーカー）をたどり、画像の大きさを書いた SOF の区切りを探す。 */
    private static ImageInfo readJpeg(byte[] d) {
        int pos = 2;
        while (true) {
            if (u8(d, pos) != 0xFF) {
                return null;
            }
            while (u8(d, pos) == 0xFF) {
                pos++; // 区切りの前の詰め物（0xFF の連続）を飛ばす
            }
            int marker = u8(d, pos);
            pos++;
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue; // 長さを持たない区切り
            }
            if (marker == 0xD9 || marker == 0xDA) {
                return null; // 大きさが書かれる前に画像の本体・終わりになった
            }
            int length = uint16be(d, pos);
            if (isStartOfFrame(marker)) {
                // 長さ（2）・精度（1）のあとに、高さ・幅（2 バイトずつ、大きい桁から）
                return new ImageInfo(ImageType.JPEG, uint16be(d, pos + 5), uint16be(d, pos + 3));
            }
            if (length < 2) {
                return null;
            }
            pos += length;
        }
    }

    /** SOF（Start Of Frame）の区切りか。C4（ハフマン表）・C8・CC（算術符号の表）は除く。 */
    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    /** WebP：最初のかたまり（VP8 ・VP8L・VP8X）の種類によって、幅・高さの書き方が違う。 */
    private static ImageInfo readWebp(byte[] d) {
        String chunk = new String(d, 12, 4, StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8 " -> {
                // 非可逆圧縮。3 バイトの見出しと開始の印（9D 01 2A）のあとに、幅・高さ（14 ビットずつ）
                if (u8(d, 23) != 0x9D || u8(d, 24) != 0x01 || u8(d, 25) != 0x2A) {
                    yield null;
                }
                yield new ImageInfo(ImageType.WEBP, uint16le(d, 26) & 0x3FFF, uint16le(d, 28) & 0x3FFF);
            }
            case "VP8L" -> {
                // 可逆圧縮。印（0x2F）のあとに、「幅 - 1」「高さ - 1」（14 ビットずつ）
                if (u8(d, 20) != 0x2F) {
                    yield null;
                }
                int bits = int32le(d, 21);
                yield new ImageInfo(ImageType.WEBP, (bits & 0x3FFF) + 1, ((bits >>> 14) & 0x3FFF) + 1);
            }
            case "VP8X" ->
                // 拡張形式（アニメーション・透過など）。「幅 - 1」「高さ - 1」（3 バイトずつ、小さい桁から）
                new ImageInfo(ImageType.WEBP, uint24le(d, 24) + 1, uint24le(d, 27) + 1);
            default -> null;
        };
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.IMAGE_TYPE_INVALID);
    }

    private static boolean startsWith(byte[] d, byte[] prefix) {
        return d.length >= prefix.length && Arrays.equals(d, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    /** 範囲の外を読んだら IndexOutOfBoundsException（壊れたファイル）になるよう、配列を直接読む。 */
    private static int u8(byte[] d, int i) {
        return d[i] & 0xFF;
    }

    private static int uint16be(byte[] d, int i) {
        return (u8(d, i) << 8) | u8(d, i + 1);
    }

    private static int uint16le(byte[] d, int i) {
        return u8(d, i) | (u8(d, i + 1) << 8);
    }

    private static int uint24le(byte[] d, int i) {
        return u8(d, i) | (u8(d, i + 1) << 8) | (u8(d, i + 2) << 16);
    }

    private static int int32be(byte[] d, int i) {
        return (u8(d, i) << 24) | (u8(d, i + 1) << 16) | (u8(d, i + 2) << 8) | u8(d, i + 3);
    }

    private static int int32le(byte[] d, int i) {
        return u8(d, i) | (u8(d, i + 1) << 8) | (u8(d, i + 2) << 16) | (u8(d, i + 3) << 24);
    }
}
