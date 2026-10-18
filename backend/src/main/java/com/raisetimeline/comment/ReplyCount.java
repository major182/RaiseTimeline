package com.raisetimeline.comment;

/** コメントごとの返信の数。コメントの ID の配列でまとめて数えた結果の1行（DB 設計書 5.9）。 */
public record ReplyCount(long parentId, long count) {}
