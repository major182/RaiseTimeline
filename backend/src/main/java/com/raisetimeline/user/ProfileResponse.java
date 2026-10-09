package com.raisetimeline.user;

import java.time.Instant;

/**
 * プロフィール（API 設計書 3.2）。利用者の要約（UserSummary）の項目に、フォロー数・フォロワー数・登録日時を足したもの。
 * 画面が同じ形で読めるよう、入れ子にせず平らに並べる。
 */
public record ProfileResponse(
        long id,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        boolean followedByMe,
        boolean isMe,
        long followingCount,
        long followerCount,
        Instant createdAt) {

    static ProfileResponse of(UserSummary summary, long followingCount, long followerCount, Instant createdAt) {
        return new ProfileResponse(
                summary.id(),
                summary.username(),
                summary.displayName(),
                summary.avatarUrl(),
                summary.bio(),
                summary.followedByMe(),
                summary.isMe(),
                followingCount,
                followerCount,
                createdAt);
    }
}
