package com.raisetimeline.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.image.ImageStorage;
import org.junit.jupiter.api.Test;

/** 画面の CSP の img-src（技術選定書 4.7）。保存先によって、画像を読み込んでよいオリジンが変わる。DB を使わない単体テスト。 */
class ContentSecurityPolicyTests {

    /** 保存先の代わり。オリジンだけを返す。 */
    private static ImageStorage storageWithOrigin(String origin) {
        return new ImageStorage() {
            @Override
            public void put(String key, byte[] data, String contentType) {}

            @Override
            public void delete(String key) {}

            @Override
            public String url(String key) {
                return "";
            }

            @Override
            public String origin() {
                return origin;
            }
        };
    }

    @Test
    void ローカルのときは自分のサイトの画像だけを許す() {
        assertThat(SecurityConfig.contentSecurityPolicy(storageWithOrigin(null)))
                .contains("img-src 'self' data: blob:;")
                .contains("script-src 'self';");
    }

    @Test
    void S3のときはバケットのオリジンからの画像も許す() {
        String origin = "https://my-bucket.s3.ap-northeast-1.amazonaws.com";
        assertThat(SecurityConfig.contentSecurityPolicy(storageWithOrigin(origin)))
                .contains("img-src 'self' data: blob: " + origin + ";")
                .contains("connect-src 'self';"); // 画像以外は広げない
    }
}
