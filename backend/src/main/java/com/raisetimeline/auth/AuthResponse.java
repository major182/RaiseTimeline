package com.raisetimeline.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** 登録・ログイン・取り直しの応答（API 設計書 3.6）。リフレッシュトークンは本文ではなく Cookie で返す。 */
@Schema(description = "登録・ログイン・取り直しの応答。リフレッシュトークンは本文ではなく Cookie で返す")
public record AuthResponse(
        @Schema(description = "アクセストークン（期限 15 分）。Authorization: Bearer で送る。画面はメモリにだけ置く") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "アクセストークンの残りの秒数", example = "900") long expiresIn,
        MeResponse user) {

    static AuthResponse of(String accessToken, long expiresIn, MeResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
