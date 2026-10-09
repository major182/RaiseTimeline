package com.raisetimeline.post;

/** 投稿ごとの数（いいねの数・コメントの数）。投稿の ID の配列でまとめて数えた結果の1行（DB 設計書 5.5）。 */
public record PostCount(long postId, long count) {}
