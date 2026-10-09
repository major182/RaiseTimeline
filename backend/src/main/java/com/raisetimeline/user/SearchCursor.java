package com.raisetimeline.user;

/**
 * 利用者の検索のカーソルの中身。読み飛ばす件数（DB 設計書 5.6）。
 * 検索は件数が少なく、並びが途中で変わっても困らないので、OFFSET で続きを取る。
 */
record SearchCursor(int offset) {

    SearchCursor {
        if (offset < 0) {
            throw new IllegalArgumentException("offset は 0 以上"); // 壊れたカーソルを 400 にするため
        }
    }
}
