package com.raisetimeline.like;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * いいね・取り消しの応答（API 設計書 4.6）。操作のあとの状態を返す。
 * 画面は返事を待たずに表示を切り替え、この値で正しい数に合わせる。
 */
@Schema(description = "操作のあとの状態")
public record LikeResponse(boolean liked, long likeCount) {}
