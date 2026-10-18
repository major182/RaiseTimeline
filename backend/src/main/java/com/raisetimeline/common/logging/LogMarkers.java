package com.raisetimeline.common.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * SLF4J にない重大度（syslog の Alert・Critical・Notice）を表す印（ログ設計書 3 章）。
 *
 * <p>SLF4J のレベルは ERROR・WARN・INFO までしかないため、この印を付けて {@link DatadogJsonFormatter} が
 * {@code status} を上書きする。例：{@code LOG.error(LogMarkers.ALERT, "...")}、{@code LOG.info(LogMarkers.NOTICE, "...")}。
 */
public final class LogMarkers {

    /** 直ちに人が対応する（アカウントの乗っ取りの疑いなど）。ERROR に付ける。 */
    public static final Marker ALERT = MarkerFactory.getMarker("ALERT");

    /** サービスの主要な機能が止まっている（画像を保存できないなど）。ERROR に付ける。 */
    public static final Marker CRITICAL = MarkerFactory.getMarker("CRITICAL");

    /** 正常だが、監査のために残す（ログインの失敗・パスワードの変更など）。INFO に付ける。 */
    public static final Marker NOTICE = MarkerFactory.getMarker("NOTICE");

    private LogMarkers() {}
}
