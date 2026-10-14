package com.raisetimeline.post;

import com.raisetimeline.user.UserSummary;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 投稿（投稿カード。API 設計書 3.4）。インプレッション数・リツイートの数は持たない（BR-40、BR-41）。
 *
 * @param images 画像（並び順のとおり）。なければ空の配列
 * @param editedAt 最後に編集した時刻。未編集なら null（BR-16）
 * @param likedVia フォロー中タブのいいね経由の投稿だけ値がある。ほかは null
 * @param isMine true のときだけ画面は編集・削除のメニューを出す（BR-12）
 */
@Schema(description = "投稿。インプレッション数・リツイートの数は持たない（BR-40、BR-41）")
public record PostResponse(
        long id,
        UserSummary author,
        String body,
        @Schema(description = "画像（並び順のとおり）。なければ空の配列") List<Image> images,
        Instant createdAt,
        @Schema(types = {"string", "null"}, format = "date-time", description = "最後に編集した時刻。未編集なら null（BR-16）")
                Instant editedAt,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        @Schema(description = "フォロー中タブのいいね経由の投稿だけ値がある。ほかは null") LikedVia likedVia,
        @Schema(description = "true のときだけ画面は編集・削除のメニューを出す（BR-12）") boolean isMine) {

    /** 画像。url は期限つきの署名つき URL（NF-SE-06）。 */
    public record Image(@Schema(description = "署名つき URL（期限 1 時間）") String url, int width, int height) {}

    /**
     * いいね経由の表示（「山田さん、ほか 2 人がいいねしました」）。
     *
     * @param othersCount 表示名を出した人のほかに、いいねしたフォロー中の人の数
     */
    @Schema(description = "「山田さん、ほか 2 人がいいねしました」の表示")
    public record LikedVia(
            String displayName,
            String username,
            @Schema(description = "表示名を出した人のほかに、いいねしたフォロー中の人の数") long othersCount) {}

    /** likedVia を付けたものを返す（フォロー中タブで使う）。 */
    public PostResponse withLikedVia(LikedVia value) {
        return new PostResponse(
                id, author, body, images, createdAt, editedAt, likeCount, commentCount, likedByMe, value, isMine);
    }
}
