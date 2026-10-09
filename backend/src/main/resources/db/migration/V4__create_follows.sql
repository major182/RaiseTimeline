-- フォロー（DB 設計書 4.6）

CREATE TABLE follows (
    follower_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 同じ人を2回フォローできないことを、主キーで守る（BR-43）
    PRIMARY KEY (follower_id, followee_id),
    -- 自分自身はフォローできない（BR-43）
    CONSTRAINT follows_not_self_check CHECK (follower_id <> followee_id)
);

-- フォロワーの一覧・フォロワー数（主キーはフォローの一覧・フォロー数に使う）
CREATE INDEX follows_followee_created_idx ON follows (followee_id, created_at DESC);
