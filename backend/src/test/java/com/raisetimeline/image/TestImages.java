package com.raisetimeline.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;

/**
 * テスト用の画像を作る道具。PNG・JPEG・GIF は Java の標準（ImageIO）で本物を作る。
 * WebP は標準で作れないため、幅・高さを読むのに必要な見出しの部分だけを組み立てる。
 */
public final class TestImages {

    private TestImages() {}

    public static byte[] png(int width, int height) {
        return write("png", width, height);
    }

    public static byte[] jpeg(int width, int height) {
        return write("jpg", width, height);
    }

    public static byte[] gif(int width, int height) {
        return write("gif", width, height);
    }

    /** 非可逆圧縮の WebP（VP8）の見出し。 */
    public static byte[] webpLossy(int width, int height) {
        ByteBuffer b = riff("VP8 ", 10);
        b.put(new byte[] {0, 0, 0}); // フレームの見出し
        b.put(new byte[] {(byte) 0x9D, 0x01, 0x2A}); // 開始の印
        b.putShort((short) width).putShort((short) height);
        return b.array();
    }

    /** 可逆圧縮の WebP（VP8L）の見出し。 */
    public static byte[] webpLossless(int width, int height) {
        ByteBuffer b = riff("VP8L", 5);
        b.put((byte) 0x2F);
        b.putInt((width - 1) | ((height - 1) << 14));
        return b.array();
    }

    /** 拡張形式の WebP（VP8X）の見出し。 */
    public static byte[] webpExtended(int width, int height) {
        ByteBuffer b = riff("VP8X", 10);
        b.putInt(0); // 印と予約
        putUint24(b, width - 1);
        putUint24(b, height - 1);
        return b.array();
    }

    /** multipart で送るファイル。ファイル名と Content-Type は、中身と違うものを付けてもよい（中身で判定するため）。 */
    public static MockMultipartFile file(String field, String filename, String contentType, byte[] data) {
        return new MockMultipartFile(field, filename, contentType, data);
    }

    public static MockMultipartFile pngFile(String field, int width, int height) {
        return file(field, "image.png", "image/png", png(width, height));
    }

    private static byte[] write(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("書き出せない形式: " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ByteBuffer riff(String chunk, int payload) {
        ByteBuffer b = ByteBuffer.allocate(20 + payload).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(12 + payload);
        b.put("WEBP".getBytes(StandardCharsets.US_ASCII));
        b.put(chunk.getBytes(StandardCharsets.US_ASCII)).putInt(payload);
        return b;
    }

    private static void putUint24(ByteBuffer b, int value) {
        b.put((byte) value).put((byte) (value >> 8)).put((byte) (value >> 16));
    }
}
