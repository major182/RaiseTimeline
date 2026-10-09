package com.raisetimeline.image;

/**
 * 画像のファイルの保存先（技術選定書 4.3）。開発・テストはローカルのフォルダ（{@link LocalImageStorage}）、
 * 本番は S3（{@link S3ImageStorage}）。どちらを使うかは app.storage.type で決める。
 * 呼ぶ側はこのインターフェースだけを使い、保存先の違いを知らなくて済むようにする。
 */
public interface ImageStorage {

    /** ファイルを保存する。同じキーがあれば上書きする。 */
    void put(String key, byte[] data, String contentType);

    /** ファイルを消す。なければ何もしない。 */
    void delete(String key);

    /** 画像を見るための、期限つきの署名つき URL（NF-SE-06）。保存先は非公開なので、この URL でだけ見られる。 */
    String url(String key);

    /**
     * 画像の URL のオリジン（例：https://バケット.s3.リージョン.amazonaws.com）。画面の CSP の img-src に足す。
     * 画面と同じオリジンから配信するとき（ローカル）は null。
     */
    default String origin() {
        return null;
    }

    /** キーがなければ（アイコンが未設定など）null を返す。 */
    default String urlOrNull(String key) {
        return key == null ? null : url(key);
    }
}
