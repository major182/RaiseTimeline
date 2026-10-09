package com.raisetimeline.auth;

/** リフレッシュトークンを無効にした理由（DB 設計書 4.7）。 */
enum RevokeReason {
    /** 取り直しで使い終わった。これが再び使われたら、盗まれたとみなす。 */
    ROTATED,
    /** ログアウトした。 */
    LOGOUT,
    /** パスワードを変えたので、他の端末のトークンを無効にした。 */
    PASSWORD_CHANGED,
    /** 使い回しを検知したので、利用者のトークンをすべて無効にした。 */
    REUSE_DETECTED
}
