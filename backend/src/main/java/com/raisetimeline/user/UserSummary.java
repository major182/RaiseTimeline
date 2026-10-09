package com.raisetimeline.user;

/**
 * 利用者の要約（API 設計書 3.1）。一覧の行・投稿者の表示に使う。メールアドレスは含めない。
 *
 * @param avatarUrl アイコンの URL。未設定なら null（画面は既定のアイコン）。画像の機能を作るまでは null
 * @param followedByMe ログインしている利用者がフォローしているか
 * @param isMe ログインしている利用者本人か（true ならフォローボタンを出さない）
 */
public record UserSummary(
        long id,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        boolean followedByMe,
        boolean isMe) {}
