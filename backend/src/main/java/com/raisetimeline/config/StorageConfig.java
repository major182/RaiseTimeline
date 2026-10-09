package com.raisetimeline.config;

import com.raisetimeline.image.ImageStorage;
import com.raisetimeline.image.LocalImageStorage;
import com.raisetimeline.image.S3ImageStorage;
import java.time.Clock;
import javax.crypto.SecretKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 画像の保存先を、設定（app.storage.type）で切り替える（技術選定書 4.3）。
 * local（既定）：ローカルのフォルダ。s3：S3。リージョンと権限は、AWS SDK が環境（EC2 の IAM ロールなど）から読む。
 */
@Configuration
public class StorageConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    ImageStorage localImageStorage(StorageProperties properties, SecretKey jwtSecretKey, Clock clock) {
        return new LocalImageStorage(properties, jwtSecretKey, clock);
    }

    @Configuration
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    static class S3 {

        @Bean(destroyMethod = "close")
        S3Client s3Client() {
            return S3Client.create();
        }

        @Bean(destroyMethod = "close")
        S3Presigner s3Presigner() {
            return S3Presigner.create();
        }

        @Bean
        ImageStorage s3ImageStorage(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
            if (properties.s3Bucket() == null || properties.s3Bucket().isBlank()) {
                throw new IllegalStateException("app.storage.s3-bucket（環境変数 S3_BUCKET）が設定されていません");
            }
            return new S3ImageStorage(s3Client, s3Presigner, properties);
        }
    }
}
