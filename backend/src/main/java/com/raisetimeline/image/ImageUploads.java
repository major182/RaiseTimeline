package com.raisetimeline.image;

import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.image.ImageInspector.ImageInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 送られた画像を確かめて保存する。DB と保存先（S3）は1つのトランザクションにできないため、
 * DB の結果に合わせて保存先のファイルを片付ける（API 設計書 4.4、DB 設計書 4.3）。
 *
 * <ul>
 *   <li>保存：DB の保存が取り消されたら（ロールバック）、保存したファイルを消す
 *   <li>削除：DB の削除が確定したら（コミット）、ファイルを消す。確定の前に消すと、DB が取り消されたときに画像だけが消えてしまう
 * </ul>
 *
 * どちらも {@code @Transactional} のメソッドの中から呼ぶこと。
 */
@Service
public class ImageUploads {

    /** 1 枚の大きさの上限（BR-22）。 */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(ImageUploads.class);

    private final ImageStorage storage;

    public ImageUploads(ImageStorage storage) {
        this.storage = storage;
    }

    /** 確かめた画像。保存する前の中身と、形式・大きさ。 */
    public record CheckedImage(byte[] data, ImageInfo info) {}

    /** 保存した画像。 */
    public record StoredImage(String key, ImageInfo info, int sizeBytes) {}

    /**
     * 画像を確かめる（BR-21・22）。大きすぎれば 413、形式が違えば 415。
     * 保存の前に全部を確かめ、1 枚でも誤りがあれば何も保存しない。
     */
    public CheckedImage check(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
        }
        try {
            byte[] data = file.getBytes();
            return new CheckedImage(data, ImageInspector.inspect(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 確かめた画像を、keyOf で決めたキーで保存する。DB の保存が取り消されたら、ファイルも消す。 */
    public StoredImage store(CheckedImage image, Function<ImageType, String> keyOf) {
        String key = keyOf.apply(image.info().type());
        storage.put(key, image.data(), image.info().type().contentType());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(List.of(key));
                }
            }
        });
        return new StoredImage(key, image.info(), image.data().length);
    }

    /** DB の削除が確定したら、ファイルを消す。 */
    public void deleteAfterCommit(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<String> copy = List.copyOf(keys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(copy);
            }
        });
    }

    /**
     * ファイルを消す。消し損ねても利用者の操作は成功させ、ログに残す
     * （残ったファイルは、後で保存先のキーの一覧と DB を突き合わせて掃除する。DB 設計書 4.3）。
     */
    private void deleteQuietly(Collection<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                LOG.warn("画像のファイルを消せませんでした: {}", key, e);
            }
        }
    }
}
