package com.raisetimeline.common.pagination;

import java.util.List;

/**
 * 一覧の応答（API 設計書 2.5）。続きは nextCursor を付けて同じ API を呼ぶ。nextCursor が null なら終わり。
 *
 * @param items 一覧の中身（最大 {@link Cursors#PAGE_SIZE} 件）
 * @param nextCursor 続きを取るためのカーソル。続きがなければ null
 */
public record CursorPage<T>(List<T> items, String nextCursor) {}
