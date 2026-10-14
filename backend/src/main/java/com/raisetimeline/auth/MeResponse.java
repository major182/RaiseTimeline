package com.raisetimeline.auth;

import com.raisetimeline.image.ImageStorage;
import com.raisetimeline.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ログインしている利用者（API 設計書 3.3）。メールアドレスを返すのはこの形だけ。
 */
@Schema(description = "ログインしている利用者")
public record MeResponse(long id, String username, String displayName, @Schema(types = {"string", "null"}, description = "アイコンの署名つき URL（期限 1 時間。NF-SE-06）。未設定なら null（画面は既定のアイコン）") String avatarUrl,
        @Schema(description = "メールアドレス。返すのはこの形だけ") String email) {

    static MeResponse from(User user, ImageStorage storage) {
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                storage.urlOrNull(user.getAvatarKey()),
                user.getEmail());
    }
}
