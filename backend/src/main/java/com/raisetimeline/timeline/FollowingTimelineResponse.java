package com.raisetimeline.timeline;

import com.raisetimeline.post.PostResponse;
import java.util.List;

/**
 * フォロー中タブの応答（API 設計書 4.3）。一覧（2.5）の形に、留守中のハイライトを足したもの。
 *
 * @param highlights 留守中のハイライト（最大 3 件）。カーソルなしで呼んだときだけ入る。続きの呼び出しでは空
 */
public record FollowingTimelineResponse(List<PostResponse> highlights, List<PostResponse> items, String nextCursor) {}
