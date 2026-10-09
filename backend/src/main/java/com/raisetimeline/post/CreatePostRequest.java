package com.raisetimeline.post;

import com.raisetimeline.common.validation.CodePointLength;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿の作成で送る値（API 設計書 4.4。multipart/form-data）。
 * 本文が空でよいのは画像があるときだけ（BR-11）。この確かめと、画像の枚数・形式・大きさの確かめはサービス層で行う。
 *
 * @param body 本文（0〜280 文字）。送られなければ空として扱う
 * @param images 画像（0〜4 枚。送った順が並び順）。送られなければ null
 */
public record CreatePostRequest(
        @CodePointLength(max = Post.BODY_MAX_LENGTH, message = "BODY_TOO_LONG") String body, List<MultipartFile> images) {

    /** 送られた画像（空のファイル欄は除く）。 */
    public List<MultipartFile> imageFiles() {
        return images == null ? List.of() : images.stream().filter(f -> !f.isEmpty()).toList();
    }
}
