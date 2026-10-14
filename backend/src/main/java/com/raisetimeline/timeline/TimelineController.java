package com.raisetimeline.timeline;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.post.PostResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.raisetimeline.common.error.ApiErrors;
import com.raisetimeline.common.error.ErrorCode;

/** タイムラインの API（API 設計書 4.3）。業務ルールは {@link TimelineService} に置く。 */
@RestController
@Tag(name = "タイムライン", description = "API 設計書 4.3")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /** フォロー中タブ（F-TL-01・07・08）。 */
    @Operation(
            summary = "フォロー中タブ",
            description = "フォロー中の人と自分の投稿、フォロー中の人のいいね経由の投稿。"
                    + "カーソルなしで呼んだときだけ留守中のハイライトを highlights に入れ、最後に開いた時刻を更新する（DB 設計書 5.3）。"
                    + "20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    @GetMapping("/api/timeline/following")
    FollowingTimelineResponse following(
            @AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String cursor) {
        return timelineService.following(cursor, me.id());
    }

    /** 全体タブ（F-TL-05）。 */
    @Operation(summary = "全体タブ", description = "全員の投稿の新しい順。20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    @GetMapping("/api/timeline/all")
    CursorPage<PostResponse> all(
            @AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String cursor) {
        return timelineService.all(cursor, me.id());
    }

    /** 利用者ごとの投稿一覧（F-TL-03）。 */
    @Operation(summary = "利用者ごとの投稿一覧", description = "新しい順。20 件ずつ。続きは nextCursor を cursor に付けて呼ぶ（API 設計書 2.5）")
    @ApiErrors({ErrorCode.VALIDATION_FAILED, ErrorCode.NOT_FOUND})
    @GetMapping("/api/users/{userId}/posts")
    CursorPage<PostResponse> byUser(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long userId,
            @RequestParam(required = false) String cursor) {
        return timelineService.byUser(userId, cursor, me.id());
    }
}
