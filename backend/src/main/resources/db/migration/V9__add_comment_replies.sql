-- コメントへの返信（DB 設計書 D-10・4.4）。返信先は同じ投稿の、返信ではないコメントだけ（BR-37。アプリで確かめる）

-- 返信先を消すと返信も消える（BR-38）
ALTER TABLE comments ADD COLUMN parent_id BIGINT REFERENCES comments (id) ON DELETE CASCADE;

-- 返信の一覧（古い順）と、返信の数（DB 設計書 5.9）
CREATE INDEX comments_parent_created_idx ON comments (parent_id, created_at, id) WHERE parent_id IS NOT NULL;
