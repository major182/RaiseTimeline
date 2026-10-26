-- 性能テストのデータの削除（性能テスト設計書 5 章）。
-- 目印の利用者（メールが @perf.example.com）と、その利用者にひもづく行だけを消す。ほかの利用者の行には触れない
-- （ただし、目印の利用者の投稿・コメントに付いた、ほかの利用者のコメント・返信・いいねは一緒に消える）。
-- 共有の環境（ステージング）でも使えるよう、1 万件ずつに分けて確定し、長いロックを避ける。
-- 条件なしの DELETE・TRUNCATE は使わない。
--
-- 画像のファイル（post_images.storage_key の指す先）はここでは消さない。
-- 保存先の接頭辞（手元は perf 用のフォルダ、S3 は perf/）ごと、scripts/perf.ts が消す。

\set ON_ERROR_STOP on

CREATE TEMP TABLE perf_users AS
SELECT id FROM users WHERE email LIKE '%@perf.example.com';

CREATE TEMP TABLE perf_posts AS
SELECT p.id FROM posts p JOIN perf_users u ON u.id = p.user_id;

DO $$
DECLARE
    deleted INTEGER;
BEGIN
    -- いいね：目印の利用者がしたもの、目印の利用者の投稿に付いたもの
    LOOP
        DELETE FROM likes
        WHERE ctid IN (
            SELECT ctid FROM likes
            WHERE user_id IN (SELECT id FROM perf_users) OR post_id IN (SELECT id FROM perf_posts)
            LIMIT 10000
        );
        GET DIAGNOSTICS deleted = ROW_COUNT;
        COMMIT;
        EXIT WHEN deleted = 0;
    END LOOP;

    -- 返信：先に消す（返信先のコメントを消すと返信も消えるが、件数の多い連鎖の削除を避ける）
    LOOP
        DELETE FROM comments
        WHERE id IN (
            SELECT id FROM comments
            WHERE parent_id IS NOT NULL
              AND (user_id IN (SELECT id FROM perf_users) OR post_id IN (SELECT id FROM perf_posts))
            LIMIT 10000
        );
        GET DIAGNOSTICS deleted = ROW_COUNT;
        COMMIT;
        EXIT WHEN deleted = 0;
    END LOOP;

    -- コメント
    LOOP
        DELETE FROM comments
        WHERE id IN (
            SELECT id FROM comments
            WHERE user_id IN (SELECT id FROM perf_users) OR post_id IN (SELECT id FROM perf_posts)
            LIMIT 10000
        );
        GET DIAGNOSTICS deleted = ROW_COUNT;
        COMMIT;
        EXIT WHEN deleted = 0;
    END LOOP;

    -- 投稿（画像の行は投稿と一緒に消える）
    LOOP
        DELETE FROM posts
        WHERE id IN (SELECT id FROM posts WHERE id IN (SELECT id FROM perf_posts) LIMIT 10000);
        GET DIAGNOSTICS deleted = ROW_COUNT;
        COMMIT;
        EXIT WHEN deleted = 0;
    END LOOP;

    -- フォロー：目印の利用者がしたもの、されたもの
    LOOP
        DELETE FROM follows
        WHERE ctid IN (
            SELECT ctid FROM follows
            WHERE follower_id IN (SELECT id FROM perf_users) OR followee_id IN (SELECT id FROM perf_users)
            LIMIT 10000
        );
        GET DIAGNOSTICS deleted = ROW_COUNT;
        COMMIT;
        EXIT WHEN deleted = 0;
    END LOOP;

    DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM perf_users);
    DELETE FROM users WHERE id IN (SELECT id FROM perf_users);
    COMMIT;
END $$;

VACUUM ANALYZE likes;
VACUUM ANALYZE comments;
VACUUM ANALYZE posts;
VACUUM ANALYZE follows;
VACUUM ANALYZE refresh_tokens;
VACUUM ANALYZE users;

-- 削除の確認（どれも 0 になる）
SELECT
    (SELECT count(*) FROM users WHERE email LIKE '%@perf.example.com') AS remaining_users,
    (SELECT count(*) FROM posts WHERE id IN (SELECT id FROM perf_posts)) AS remaining_posts;
