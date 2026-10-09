package com.raisetimeline.post;

import com.raisetimeline.user.UserSummary;
import java.time.Instant;
import java.util.List;

/**
 * 投稿（投稿カード。API 設計書 3.4）。インプレッション数・リツイートの数は持たない（BR-40、BR-41）。
 *
 * @param images 画像（並び順のとおり）。なければ空の配列
 * @param editedAt 最後に編集した時刻。未編集なら null（BR-16）
 * @param likedVia フォロー中タブのいいね経由の投稿だけ値がある。ほかは null
 * @param isMine true のときだけ画面は編集・削除のメニューを出す（BR-12）
 */
public record PostResponse(
        long id,
        UserSummary author,
        String body,
        List<Image> images,
        Instant createdAt,
        Instant editedAt,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        LikedVia likedVia,
        boolean isMine) {

    /** 画像。url は期限つきの署名つき URL（NF-SE-06）。 */
    public record Image(String url, int width, int height) {}

    /**
     * いいね経由の表示（「山田さん、ほか 2 人がいいねしました」）。
     *
     * @param othersCount 表示名を出した人のほかに、いいねしたフォロー中の人の数
     */
    public record LikedVia(String displayName, String username, long othersCount) {}

    /** likedVia を付けたものを返す（フォロー中タブで使う）。 */
    public PostResponse withLikedVia(LikedVia value) {
        return new PostResponse(
                id, author, body, images, createdAt, editedAt, likeCount, commentCount, likedByMe, value, isMine);
    }
}
