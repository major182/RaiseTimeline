package com.raisetimeline.auth;

import com.raisetimeline.user.User;

/**
 * ログインしている利用者（API 設計書 3.3）。メールアドレスを返すのはこの形だけ。
 * avatarUrl は画像の機能を作るまで null。
 */
public record MeResponse(long id, String username, String displayName, String avatarUrl, String email) {

    static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(), null, user.getEmail());
    }
}
