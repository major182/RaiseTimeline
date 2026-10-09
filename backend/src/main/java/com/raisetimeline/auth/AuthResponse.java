package com.raisetimeline.auth;

/** 登録・ログイン・取り直しの応答（API 設計書 3.6）。リフレッシュトークンは本文ではなく Cookie で返す。 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, MeResponse user) {

    static AuthResponse of(String accessToken, long expiresIn, MeResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
