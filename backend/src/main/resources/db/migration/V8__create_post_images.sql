-- 投稿の画像（DB 設計書 4.3）。ファイルは S3（開発はローカルのフォルダ）に置き、ここには保存先のキーだけを持つ（D-5）

CREATE TABLE post_images (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 投稿を消すと行も消える。ファイルはアプリが DB の削除の確定後に消す（BR-13）
    post_id      BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- 並び順。1〜4（BR-20）
    position     SMALLINT    NOT NULL CONSTRAINT post_images_position_check CHECK (position BETWEEN 1 AND 4),
    storage_key  TEXT        NOT NULL,
    -- 中身で判定した形式（BR-21）
    content_type TEXT        NOT NULL CONSTRAINT post_images_content_type_check
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/gif')),
    -- 1 枚 5MB まで（BR-22）
    size_bytes   INTEGER     NOT NULL CONSTRAINT post_images_size_bytes_check CHECK (size_bytes BETWEEN 1 AND 5242880),
    width        INTEGER     NOT NULL CONSTRAINT post_images_width_check CHECK (width >= 1),
    height       INTEGER     NOT NULL CONSTRAINT post_images_height_check CHECK (height >= 1),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT post_images_post_position_key UNIQUE (post_id, position),
    CONSTRAINT post_images_storage_key_key UNIQUE (storage_key)
);
