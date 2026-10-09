package com.raisetimeline.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.raisetimeline.TestcontainersConfiguration;
import com.raisetimeline.common.error.ApiException;
import com.raisetimeline.common.error.ErrorCode;
import com.raisetimeline.config.StorageProperties;
import com.raisetimeline.image.ImageUploads.StoredImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 画像の確かめと、DB の結果に合わせた保存先の片付け（API 設計書 4.4、DB 設計書 4.3）。
 * DB の保存が取り消されたら保存したファイルを消し、DB の削除が確定したらファイルを消す。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ImageUploadsTests {

    @Autowired
    private ImageUploads uploads;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private StorageProperties properties;

    private Path fileOf(String key) {
        return properties.localDir().resolve(key);
    }

    private StoredImage storeInTransaction(boolean rollback) {
        return transaction.execute(status -> {
            StoredImage stored = uploads.store(
                    uploads.check(TestImages.pngFile("file", 4, 3)), type -> ImageKeys.post(type, Instant.now()));
            if (rollback) {
                status.setRollbackOnly();
            }
            return stored;
        });
    }

    @Test
    void 確定したら保存したファイルは残る() {
        StoredImage stored = storeInTransaction(false);
        assertThat(fileOf(stored.key())).exists();
        assertThat(stored.info().width()).isEqualTo(4);
        assertThat(stored.key()).endsWith(".png");
    }

    @Test
    void 取り消されたら保存したファイルを消す() {
        StoredImage stored = storeInTransaction(true);
        assertThat(fileOf(stored.key())).doesNotExist();
    }

    @Test
    void 削除は確定してからファイルを消す() {
        StoredImage stored = storeInTransaction(false);
        transaction.executeWithoutResult(status -> {
            uploads.deleteAfterCommit(List.of(stored.key()));
            assertThat(fileOf(stored.key())).exists(); // 確定の前はまだ消さない
        });
        assertThat(fileOf(stored.key())).doesNotExist();
    }

    @Test
    void 削除が取り消されたらファイルは残す() {
        StoredImage stored = storeInTransaction(false);
        transaction.executeWithoutResult(status -> {
            uploads.deleteAfterCommit(List.of(stored.key()));
            status.setRollbackOnly();
        });
        assertThat(fileOf(stored.key())).exists();
    }

    @Test
    void 五MBを超える画像は413() {
        byte[] big = new byte[(int) ImageUploads.MAX_BYTES + 1];
        assertThatThrownBy(() -> uploads.check(TestImages.file("file", "big.png", "image/png", big)))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE));
    }

    @Test
    void ちょうど5MBまでは大きさの誤りにしない() throws Exception {
        byte[] png = TestImages.png(2, 2);
        byte[] exact = new byte[(int) ImageUploads.MAX_BYTES];
        System.arraycopy(png, 0, exact, 0, png.length); // 先頭は本物の PNG、残りは 0 で埋める
        assertThat(uploads.check(TestImages.file("file", "exact.png", "image/png", exact)).info().type())
                .isEqualTo(ImageType.PNG);
        assertThat(Files.exists(properties.localDir())).isTrue();
    }
}
