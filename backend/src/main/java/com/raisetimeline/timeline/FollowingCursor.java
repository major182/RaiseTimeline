package com.raisetimeline.timeline;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * フォロー中タブのカーソルの中身（API 設計書 4.3、DB 設計書 5.2）。
 *
 * @param base 基準の時刻（最初に読み込んだ時刻）。読み進める間についた新しいいいねで、投稿が上に移動しないようにする
 * @param at 最後に返した行の並びの時刻
 * @param id 最後に返した行の投稿の ID
 * @param highlightIds ハイライトに出した投稿の ID。一覧には出さない（BR-52-1）
 */
record FollowingCursor(Instant base, Instant at, long id, List<Long> highlightIds) {

    FollowingCursor {
        // 壊れたカーソル（値がない）を 400 にするため
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(at, "at");
        highlightIds = highlightIds == null ? List.of() : List.copyOf(highlightIds);
    }
}
