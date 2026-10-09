package com.raisetimeline.comment;

import java.time.Instant;
import java.util.Objects;

/**
 * コメントの一覧のカーソルの中身。最後に返したコメントの時刻と ID。
 *
 * @param at コメントした時刻
 * @param id コメントの ID
 */
record CommentCursor(Instant at, long id) {

    CommentCursor {
        Objects.requireNonNull(at, "at"); // 壊れたカーソル（時刻がない）を 400 にするため
    }
}
