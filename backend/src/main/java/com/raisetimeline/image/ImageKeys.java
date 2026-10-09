package com.raisetimeline.image;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 保存先のキー（S3 上の名前）を作る（DB 設計書 4.3）。名前は推測できないランダムな値にする。
 * 拡張子は、中身で判定した形式から付ける（送られたファイル名は使わない）。
 */
public final class ImageKeys {

    private ImageKeys() {}

    /** 投稿の画像：posts/2026/10/ランダムな値.webp */
    public static String post(ImageType type, Instant now) {
        ZonedDateTime at = now.atZone(ZoneOffset.UTC);
        return "posts/%04d/%02d/%s.%s".formatted(at.getYear(), at.getMonthValue(), UUID.randomUUID(), type.extension());
    }

    /** アイコン：avatars/ランダムな値.png */
    public static String avatar(ImageType type) {
        return "avatars/%s.%s".formatted(UUID.randomUUID(), type.extension());
    }
}
