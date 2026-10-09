package com.raisetimeline.image;

/** 受け付ける画像の形式（BR-21）。形式はファイル名の拡張子ではなく、中身で判定する（{@link ImageInspector}）。 */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif");

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    /** 保存先のキーに付ける拡張子（中身で判定した形式から決める）。 */
    public String extension() {
        return extension;
    }
}
