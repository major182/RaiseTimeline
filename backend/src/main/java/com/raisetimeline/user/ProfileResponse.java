package com.raisetimeline.user;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * プロフィール（API 設計書 3.2）。利用者の要約（UserSummary）の項目に、フォロー数・フォロワー数・登録日時を足したもの。
 * 画面が同じ形で読めるよう、入れ子にせず平らに並べる。
 */
@Schema(description = "プロフィール（UserSummary の項目に、フォロー数・フォロワー数・登録日時を足したもの）")
public record ProfileResponse(
        long id,
        String username,
        String displayName,
        @Schema(types = {"string", "null"}, description = "アイコンの署名つき URL（期限 1 時間。NF-SE-06）。未設定なら null（画面は既定のアイコン）") String avatarUrl,
        String bio,
        boolean followedByMe,
        @Schema(description = "ログインしている利用者本人か") boolean isMe,
        long followingCount,
        long followerCount,
        @Schema(description = "登録日時") Instant createdAt) {

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
