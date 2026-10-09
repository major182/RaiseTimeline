package com.raisetimeline.like;

import java.time.Instant;
import java.util.Objects;

/**
 * いいねした人の一覧のカーソルの中身。最後に返した行の「いいねした時刻」と「利用者の ID」。
 *
 * @param at いいねした時刻
 * @param id いいねした利用者の ID
 */
record LikeCursor(Instant at, long id) {

    LikeCursor {
        Objects.requireNonNull(at, "at"); // 壊れたカーソル（時刻がない）を 400 にするため
    }
}
