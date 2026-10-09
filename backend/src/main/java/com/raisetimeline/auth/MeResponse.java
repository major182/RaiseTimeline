package com.raisetimeline.auth;

import com.raisetimeline.image.ImageStorage;
import com.raisetimeline.user.User;

/**
 * ログインしている利用者（API 設計書 3.3）。メールアドレスを返すのはこの形だけ。
 */
public record MeResponse(long id, String username, String displayName, String avatarUrl, String email) {

    static MeResponse from(User user, ImageStorage storage) {
        return new MeResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                storage.urlOrNull(user.getAvatarKey()),
                user.getEmail());
    }
}
