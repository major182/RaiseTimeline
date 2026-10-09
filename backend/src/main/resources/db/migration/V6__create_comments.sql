-- コメント（DB 設計書 4.4）

CREATE TABLE comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 投稿を消すとコメントも消える（BR-13）
    post_id    BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL CONSTRAINT comments_body_check CHECK (char_length(body) BETWEEN 1 AND 280),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 投稿の詳細でのコメントの一覧（古い順）と、コメントの数
CREATE INDEX comments_post_created_idx ON comments (post_id, created_at, id);
