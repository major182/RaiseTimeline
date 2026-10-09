package com.raisetimeline.image;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ローカルに保存した画像を返す（開発環境だけ。API 設計書 4.8）。本番は S3 の署名つき URL を使うので、この API はない。
 *
 * <p>{@code <img>} は Authorization ヘッダーを送れないため、ログインではなく、URL の署名と期限で守る（{@link LocalImageStorage}）。
 * 署名が違う・期限が切れた・ないときは、どれも 404 にする（どれに当たったかを知らせない）。
 */
@RestController
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class MediaController {

    private final LocalImageStorage storage;

    public MediaController(ImageStorage storage) {
        this.storage = (LocalImageStorage) storage;
    }

    @GetMapping("/media/{*key}")
    ResponseEntity<byte[]> get(
            @PathVariable String key,
            @RequestParam(defaultValue = "0") long expires,
            @RequestParam(required = false) String signature) {
        String normalized = key.startsWith("/") ? key.substring(1) : key; // {*key} は先頭の / を含む
        return storage.read(normalized, expires, signature)
                .map(data -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentTypeOf(normalized)))
                        // 本人のブラウザにだけ、URL の期限より短い間とっておく
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(data))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 保存するときに中身で判定した形式を、拡張子にして付けてある（{@link ImageKeys}）。 */
    private static String contentTypeOf(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1);
        for (ImageType type : ImageType.values()) {
            if (type.extension().equals(extension)) {
                return type.contentType();
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
