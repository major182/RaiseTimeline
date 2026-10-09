-- 投稿（DB 設計書 4.2）

CREATE TABLE posts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 0 文字は画像だけの投稿のとき（BR-11。画像があるかは別のテーブルのため、アプリで確かめる）
    body       TEXT        NOT NULL DEFAULT '' CONSTRAINT posts_body_check CHECK (char_length(body) <= 280),
    -- 最後に編集した時刻。NULL なら未編集（BR-16）
    edited_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 全体タブ（DB 設計書 5.1）
CREATE INDEX posts_created_idx ON posts (created_at DESC, id DESC);
-- フォロー中タブ・利用者ごとの投稿一覧（DB 設計書 5.2、5.4）
CREATE INDEX posts_user_created_idx ON posts (user_id, created_at DESC, id DESC);
