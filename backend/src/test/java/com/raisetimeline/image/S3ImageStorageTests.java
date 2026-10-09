package com.raisetimeline.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.raisetimeline.config.StorageProperties;
import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 への保存（本番。技術選定書 4.3）。AWS には接続しない単体テスト。
 * S3Client は偽物（Mockito）にし、署名つき URL は手元で計算できるので本物の S3Presigner で作る。
 */
class S3ImageStorageTests {

    private static final String KEY = "posts/2026/10/123e4567-e89b-12d3-a456-426614174000.webp";

    private S3Client s3;
    private S3Presigner presigner;
    private S3ImageStorage storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
        storage = new S3ImageStorage(
                s3, presigner, new StorageProperties("s3", null, "test-bucket", Duration.ofHours(1)));
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void バケットとキーと形式を指定して保存する() {
        storage.put(KEY, new byte[] {1, 2, 3}, "image/webp");
        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest.Builder builder = PutObjectRequest.builder();
        captor.getValue().accept(builder);
        PutObjectRequest request = builder.build();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(KEY);
        assertThat(request.contentType()).isEqualTo("image/webp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void バケットとキーを指定して消す() {
        storage.delete(KEY);
        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3).deleteObject(captor.capture());
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().bucket()).isEqualTo("test-bucket");
        assertThat(builder.build().key()).isEqualTo(KEY);
    }

    @Test
    void 期限1時間の署名つきURLを返す() {
        URI url = URI.create(storage.url(KEY));
        assertThat(url.getHost()).startsWith("test-bucket.s3");
        assertThat(url.getPath()).isEqualTo("/" + KEY);
        assertThat(url.getQuery()).contains("X-Amz-Expires=3600").contains("X-Amz-Signature=");
    }

    @Test
    void オリジンはバケットのホスト() {
        assertThat(storage.origin()).isEqualTo("https://test-bucket.s3.ap-northeast-1.amazonaws.com");
    }
}
