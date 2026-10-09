package com.raisetimeline.timeline;

import java.time.Instant;
import java.util.Objects;

/**
 * 全体タブ・利用者ごとの投稿一覧のカーソルの中身。最後に返した投稿の投稿日時と ID（DB 設計書 5.1）。
 *
 * @param at 投稿日時
 * @param id 投稿の ID
 */
record PostCursor(Instant at, long id) {

    PostCursor {
        Objects.requireNonNull(at, "at"); // 壊れたカーソル（時刻がない）を 400 にするため
    }
}
