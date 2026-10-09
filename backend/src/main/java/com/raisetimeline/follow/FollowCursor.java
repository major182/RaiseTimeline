package com.raisetimeline.follow;

import java.time.Instant;
import java.util.Objects;

/**
 * フォロー／フォロワーの一覧のカーソルの中身。最後に返した行の「フォローした時刻」と「相手の利用者の ID」。
 *
 * @param at フォローした時刻
 * @param id 一覧に出した利用者の ID
 */
record FollowCursor(Instant at, long id) {

    FollowCursor {
        Objects.requireNonNull(at, "at"); // 壊れたカーソル（時刻がない）を 400 にするため
    }
}
