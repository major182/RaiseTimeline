package com.raisetimeline.timeline;

import com.raisetimeline.post.PostResponse;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * フォロー中タブの応答（API 設計書 4.3）。一覧（2.5）の形に、留守中のハイライトを足したもの。
 *
 * @param highlights 留守中のハイライト（最大 3 件）。カーソルなしで呼んだときだけ入る。続きの呼び出しでは空
 */
public record FollowingTimelineResponse(
        @Schema(description = "留守中のハイライト（最大 3 件）。カーソルなしで呼んだときだけ入る。続きの呼び出しでは空")
                List<PostResponse> highlights,
        List<PostResponse> items,
        @Schema(types = {"string", "null"}, description = "続きを取るためのカーソル。続きがなければ null") String nextCursor) {}
