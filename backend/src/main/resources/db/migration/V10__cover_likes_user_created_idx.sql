-- フォロー中の人のいいねを探す索引に post_id を含める（DB 設計書 5.2、#90）。
-- フォロー中タブの「いいね経由の投稿」は、フォロー中の人のいいねを投稿ごとに集計する。post_id を索引に含めると、
-- 表（likes）を読まずに索引だけで集計できる（性能テスト用のデータで、フォロー中タブの SQL が 27ms → 15ms）
DROP INDEX likes_user_created_idx;
CREATE INDEX likes_user_created_idx ON likes (user_id, created_at DESC) INCLUDE (post_id);
