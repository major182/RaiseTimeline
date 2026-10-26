-- 性能テスト用のテストデータ（性能テスト設計書 4 章）。node scripts/perf.mjs が、性能テスト用の DB に入れる。
-- 開発用のテストデータ（db/dev-data）とは分け、Flyway では読み込まない。
--
-- 目印：利用者のメールは perf-0001@perf.example.com、ユーザー名は perf_0001 の形（連番は 0001〜1000）。
-- 投稿・いいね・コメント・フォローはすべて目印の利用者にひもづくので、cleanup.sql で目印の利用者のものだけを消せる。
-- パスワードはどれも pass1234（開発用のテストデータと同じハッシュ）。
--
-- 件数：利用者 1,000、フォロー 50,000（1 人 50）、投稿 100,000、いいね 500,000（1 人 500）、
--       コメント 50,000、返信 10,000。投稿の日時は直近 30 日に散らす。

\set ON_ERROR_STOP on

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE email LIKE '%@perf.example.com') THEN
        RAISE EXCEPTION '性能テストのデータがすでにあります。先に cleanup.sql で消してください';
    END IF;
END $$;

BEGIN;

INSERT INTO users (username, display_name, email, password_hash, bio, created_at, updated_at)
SELECT format('perf_%s', lpad(n::text, 4, '0')),
       format('性能テスト %s', n),
       format('perf-%s@perf.example.com', lpad(n::text, 4, '0')),
       '$2a$10$SR71e029/rhInSjaDZPBAeNe0J8i4m/uJv//DUZWZls8OGbueCOEq',
       '性能テスト用の利用者です',
       now() - interval '60 days',
       now() - interval '60 days'
FROM generate_series(1, 1000) AS n;

-- 連番（1〜1000）と利用者の ID の対応
CREATE TEMP TABLE perf_users ON COMMIT DROP AS
SELECT substring(username FROM 6)::int AS n, id
FROM users
WHERE email LIKE '%@perf.example.com';

-- フォロー：連番 n の人が、n+1〜n+50 の人（1000 を超えたら先頭に戻る）をフォローする
INSERT INTO follows (follower_id, followee_id, created_at)
SELECT a.id, b.id, now() - interval '30 days'
FROM perf_users a
CROSS JOIN generate_series(1, 50) AS k
JOIN perf_users b ON b.n = (a.n + k - 1) % 1000 + 1;

-- 投稿：100 件ずつ順番に、全員の投稿を直近 30 日に散らす
INSERT INTO posts (user_id, body, created_at, updated_at)
SELECT u.id,
       format('性能テストの投稿 %s です。#perf', i),
       now() - (100000 - i) * interval '25.92 seconds',
       now() - (100000 - i) * interval '25.92 seconds'
FROM generate_series(1, 100000) AS i
JOIN perf_users u ON u.n = (i - 1) % 1000 + 1;

-- 投稿の通し番号（1〜100000）と投稿の ID の対応
CREATE TEMP TABLE perf_posts ON COMMIT DROP AS
SELECT row_number() OVER (ORDER BY p.id) AS i, p.id
FROM posts p
JOIN perf_users u ON u.id = p.user_id;

-- いいね：1 人 500 件。k*101 の間隔で選ぶので、同じ人が同じ投稿を 2 回選ぶことはない
INSERT INTO likes (post_id, user_id, created_at)
SELECT p.id, u.id, now() - k * interval '1 minute'
FROM perf_users u
CROSS JOIN generate_series(1, 500) AS k
JOIN perf_posts p ON p.i = (u.n * 7919 + k * 101) % 100000 + 1;

-- コメント：新しい方から 50,000 件の投稿に 1 件ずつ
INSERT INTO comments (post_id, user_id, body, created_at, updated_at)
SELECT p.id, u.id, format('性能テストのコメント %s', p.i), now() - (100000 - p.i) * interval '25 seconds', now() - (100000 - p.i) * interval '25 seconds'
FROM perf_posts p
JOIN perf_users u ON u.n = (p.i * 13) % 1000 + 1
WHERE p.i > 50000;

-- 返信：コメントのうち 10,000 件に 1 件ずつ
INSERT INTO comments (post_id, user_id, parent_id, body, created_at, updated_at)
SELECT c.post_id, u.id, c.id, format('@perf_%s 返信です', lpad(((c.id % 1000) + 1)::text, 4, '0')), c.created_at + interval '1 minute', c.created_at + interval '1 minute'
FROM comments c
JOIN perf_users cu ON cu.id = c.user_id
JOIN perf_users u ON u.n = (c.id * 17) % 1000 + 1
WHERE c.parent_id IS NULL AND c.id % 5 = 0;

COMMIT;

ANALYZE users;
ANALYZE follows;
ANALYZE posts;
ANALYZE likes;
ANALYZE comments;

SELECT
    (SELECT count(*) FROM users WHERE email LIKE '%@perf.example.com') AS users,
    (SELECT count(*) FROM follows f JOIN users u ON u.id = f.follower_id WHERE u.email LIKE '%@perf.example.com') AS follows,
    (SELECT count(*) FROM posts p JOIN users u ON u.id = p.user_id WHERE u.email LIKE '%@perf.example.com') AS posts,
    (SELECT count(*) FROM likes l JOIN users u ON u.id = l.user_id WHERE u.email LIKE '%@perf.example.com') AS likes,
    (SELECT count(*) FROM comments c JOIN users u ON u.id = c.user_id WHERE u.email LIKE '%@perf.example.com') AS comments;
