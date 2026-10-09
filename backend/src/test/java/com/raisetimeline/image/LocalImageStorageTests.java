package com.raisetimeline.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.raisetimeline.config.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/** ローカルのフォルダへの保存と、署名つき URL（技術選定書 4.3、NF-SE-06）。DB を使わない単体テスト。 */
class LocalImageStorageTests {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    private static final String KEY = "posts/2026/10/123e4567-e89b-12d3-a456-426614174000.png";

    @TempDir
    Path dir;

    private LocalImageStorage storage;
    private LocalImageStorage later; // 2 時間後（期限の 1 時間を過ぎた）

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties("local", dir, null, Duration.ofHours(1));
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode("nvFB7cPXE+a+1YqaHZJbYbgDi178gUgtW6LhQZpyHrA="), "HmacSHA256");
        storage = new LocalImageStorage(properties, key, Clock.fixed(NOW, ZoneOffset.UTC));
        later = new LocalImageStorage(properties, key, Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC));
    }

    private static UriComponents parse(String url) {
        return UriComponentsBuilder.fromUriString(url).build();
    }

    private static long expires(String url) {
        return Long.parseLong(parse(url).getQueryParams().getFirst("expires"));
    }

    private static String signature(String url) {
        return parse(url).getQueryParams().getFirst("signature");
    }

    @Test
    void 保存したファイルを署名つきURLで読める() throws Exception {
        storage.put(KEY, new byte[] {1, 2, 3}, "image/png");
        assertThat(Files.readAllBytes(dir.resolve(KEY))).containsExactly(1, 2, 3);

        String url = storage.url(KEY);
        assertThat(parse(url).getPath()).isEqualTo("/media/" + KEY);
        assertThat(expires(url)).isEqualTo(NOW.plus(Duration.ofHours(1)).getEpochSecond());
        assertThat(storage.read(KEY, expires(url), signature(url))).hasValueSatisfying(
                data -> assertThat(data).containsExactly(1, 2, 3));
    }

    @Test
    void 署名や期限を書き換えると読めない() {
        storage.put(KEY, new byte[] {1}, "image/png");
        String url = storage.url(KEY);
        assertThat(storage.read(KEY, expires(url) + 3600, signature(url))).isEmpty(); // 期限を延ばす
        assertThat(storage.read(KEY, expires(url), signature(url) + "x")).isEmpty();
        assertThat(storage.read(KEY, expires(url), null)).isEmpty();
        String other = "posts/2026/10/00000000-0000-0000-0000-000000000000.png";
        storage.put(other, new byte[] {2}, "image/png");
        assertThat(storage.read(other, expires(url), signature(url))).isEmpty(); // 別のキーに使い回す
    }

    @Test
    void 期限が切れたURLでは読めない() {
        storage.put(KEY, new byte[] {1}, "image/png");
        String url = storage.url(KEY);
        assertThat(later.read(KEY, expires(url), signature(url))).isEmpty();
    }

    @Test
    void 消したファイルは読めず二回消しても誤りにしない() {
        storage.put(KEY, new byte[] {1}, "image/png");
        String url = storage.url(KEY);
        storage.delete(KEY);
        storage.delete(KEY);
        assertThat(storage.read(KEY, expires(url), signature(url))).isEmpty();
    }

    @Test
    void 形の違うキーはフォルダの外を指せない() {
        assertThatThrownBy(() -> storage.put("../escape.png", new byte[] {1}, "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.read("../../etc/passwd", Long.MAX_VALUE, "x")).isEmpty();
        storage.delete("../escape.png"); // 何もしない
    }

    @Test
    void キーがなければURLはnull() {
        assertThat(storage.urlOrNull(null)).isNull();
        assertThat(storage.urlOrNull(KEY)).startsWith("/media/");
    }
}
