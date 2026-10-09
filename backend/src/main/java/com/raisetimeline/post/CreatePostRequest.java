package com.raisetimeline.post;

import com.raisetimeline.common.validation.CodePointLength;

/**
 * 投稿の作成で送る値（API 設計書 4.4。multipart/form-data）。
 * 本文が空でよいのは画像があるときだけ（BR-11）。この確かめはサービス層で行う。
 *
 * @param body 本文（0〜280 文字）。送られなければ空として扱う
 */
public record CreatePostRequest(@CodePointLength(max = Post.BODY_MAX_LENGTH, message = "BODY_TOO_LONG") String body) {}
