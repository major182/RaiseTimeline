package com.raisetimeline.image;

import com.raisetimeline.config.StorageProperties;
import java.net.URI;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 に保存する（本番。技術選定書 4.3）。バケットは非公開にし、画像は期限つきの署名つき URL で見せる（NF-SE-06）。
 * S3 へのアクセスの権限は、EC2 に付けた IAM ロールから AWS SDK が自動で読む（鍵をコードや設定に書かない。NF-SE-07）。
 */
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3ImageStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        s3.putObject(
                request -> request.bucket(properties.s3Bucket()).key(key).contentType(contentType),
                RequestBody.fromBytes(data));
    }

    @Override
    public void delete(String key) {
        // S3 はないキーを消しても誤りにしない
        s3.deleteObject(request -> request.bucket(properties.s3Bucket()).key(key));
    }

    /** 署名つき URL と同じ形のオリジン。試しに URL を作り、そのスキームとホストを使う（リージョンの書き方を SDK に任せる）。 */
    @Override
    public String origin() {
        URI url = URI.create(url("origin-probe"));
        return url.getScheme() + "://" + url.getAuthority();
    }

    @Override
    public String url(String key) {
        return presigner
                .presignGetObject(presign -> presign.signatureDuration(properties.urlTtl())
                        .getObjectRequest(get -> get.bucket(properties.s3Bucket()).key(key)))
                .url()
                .toString();
    }
}
