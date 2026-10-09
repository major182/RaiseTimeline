package com.raisetimeline.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * タイムラインの設定（application.yaml の app.timeline。BR-52、要件定義書 Q-07）。
 *
 * @param highlightAfter 最後にフォロー中タブを開いてから、これだけたっていたら留守中のハイライトを出す
 * @param highlightCount 留守中のハイライトの最大の件数
 */
@ConfigurationProperties("app.timeline")
public record TimelineProperties(Duration highlightAfter, int highlightCount) {}
