package com.raisetimeline.image;

import com.raisetimeline.config.StorageProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * ローカルのフォルダに保存する（開発・テスト用。技術選定書 4.3）。
 *
 * <p>画像の URL は、S3 の署名つき URL と同じ考え方で、期限（expires）と署名（signature）を付けた
 * {@code /media/{key}?expires=...&signature=...} にする。画面の {@code <img>} は Authorization ヘッダー
 * （アクセストークン）を送れないため、ログインの代わりに署名で「このサーバーが発行した URL か」を確かめる（{@link MediaController}）。
 * 署名の鍵は、アクセストークンの署名の鍵（app.auth.jwt-secret）を使う。
 */
public class LocalImageStorage implements ImageStorage {

    /** 受け付けるキーの形（{@link ImageKeys}）。フォルダの外を指す「..」などを通さない。 */
    private static final Pattern KEY = Pattern.compile("^(posts/\\d{4}/\\d{2}|avatars)/[0-9a-f-]{36}\\.(jpg|png|webp|gif)$");

    private final Path root;
    private final SecretKey signingKey;
    private final StorageProperties properties;
    private final Clock clock;

    public LocalImageStorage(StorageProperties properties, SecretKey signingKey, Clock clock) {
        this.root = properties.localDir().toAbsolutePath().normalize();
        this.signingKey = signingKey;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Path path = resolve(key).orElseThrow(() -> new IllegalArgumentException("キーの形が違います: " + key));
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        resolve(key).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public String url(String key) {
        long expires = clock.instant().plus(properties.urlTtl()).getEpochSecond();
        return "/media/" + key + "?expires=" + expires + "&signature=" + sign(key, expires);
    }

    /**
     * 署名つき URL で求められたファイルを読む。署名が違う・期限が切れた・形の違うキー・ファイルがないときは空。
     */
    Optional<byte[]> read(String key, long expires, String signature) {
        if (signature == null
                // 秒の数のまま比べる（Instant に変えると、極端な値を送られたときに例外になるため）
                || expires < clock.instant().getEpochSecond()
                || !MessageDigest.isEqual(
                        sign(key, expires).getBytes(StandardCharsets.US_ASCII),
                        signature.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        return resolve(key).filter(Files::isRegularFile).map(path -> {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** キーの形を確かめてから、保存先のフォルダの中のパスにする。 */
    private Optional<Path> resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        return Optional.of(root.resolve(key));
    }

    /** キーと期限に、HMAC-SHA256 で署名する。キーや期限を書き換えると署名が合わなくなる。 */
    private String sign(String key, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] digest = mac.doFinal((key + "\n" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e); // HmacSHA256 は Java に必ずあり、鍵は起動時に確かめてある
        }
    }
}
