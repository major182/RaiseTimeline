-- いいね（DB 設計書 4.5）

CREATE TABLE likes (
    -- 投稿を消すといいねも消える（BR-13）
    post_id    BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- いいねした時刻。フォロー中タブの「いいね経由の投稿」の並びに使う（BR-50-3）
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 同じ人が同じ投稿に2回いいねできないことを、主キーで守る（BR-33）
    PRIMARY KEY (post_id, user_id)
);

-- フォロー中の人がいいねした投稿を探す（DB 設計書 5.2）
CREATE INDEX likes_user_created_idx ON likes (user_id, created_at DESC);
