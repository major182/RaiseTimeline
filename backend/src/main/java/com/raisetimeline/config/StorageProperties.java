package com.raisetimeline.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 画像の保存先の設定（application.yaml の app.storage。技術選定書 4.3）。
 *
 * @param type 保存先。local（ローカルのフォルダ。開発・テスト）か s3（本番）
 * @param localDir local のときの保存先のフォルダ
 * @param s3Bucket s3 のときのバケットの名前（非公開のバケット）
 * @param urlTtl 署名つき URL の期限（NF-SE-06）
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(String type, Path localDir, String s3Bucket, Duration urlTtl) {}
