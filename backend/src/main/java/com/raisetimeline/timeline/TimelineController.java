package com.raisetimeline.timeline;

import com.raisetimeline.auth.AuthenticatedUser;
import com.raisetimeline.common.pagination.CursorPage;
import com.raisetimeline.post.PostResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** タイムラインの API（API 設計書 4.3）。業務ルールは {@link TimelineService} に置く。 */
@RestController
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /** フォロー中タブ（F-TL-01・07・08）。 */
    @GetMapping("/api/timeline/following")
    FollowingTimelineResponse following(
            @AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String cursor) {
        return timelineService.following(cursor, me.id());
    }

    /** 全体タブ（F-TL-05）。 */
    @GetMapping("/api/timeline/all")
    CursorPage<PostResponse> all(
            @AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String cursor) {
        return timelineService.all(cursor, me.id());
    }

    /** 利用者ごとの投稿一覧（F-TL-03）。 */
    @GetMapping("/api/users/{userId}/posts")
    CursorPage<PostResponse> byUser(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable long userId,
            @RequestParam(required = false) String cursor) {
        return timelineService.byUser(userId, cursor, me.id());
    }
}
