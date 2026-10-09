-- 開発用のテストデータ（DB 設計書 7 章）。開発のプロファイル（./gradlew bootRun）のときだけ読み込む。本番には入れない。
--
-- Flyway の「繰り返し実行するマイグレーション」（R__）にしている。V__ と番号を分けるので、
-- 今後マイグレーション（V9 など）を足してもぶつからない。この SQL を書き換えると、次の起動でもう一度実行される。
-- テストデータの利用者がすでにいれば何もしない（2 回入らないように）。
--
-- 利用者（パスワードはどれも pass1234）
--   sato_hana  （佐藤 花）：鈴木をフォロー。最後にタイムラインを開いたのは 7 時間前（留守中のハイライトが出る）
--   suzuki_ken （鈴木 健）：佐藤・田中をフォロー
--   tanaka_yu  （田中 悠）：佐藤をフォロー。投稿が 30 件（全体タブで続きの読み込みを試せる）
-- 佐藤のフォロー中タブには、鈴木がいいねした田中の投稿が「鈴木 健さんがいいねしました」と出る（いいね経由）。

DO $$
DECLARE
    -- pass1234 の BCrypt のハッシュ（開発用。本番の利用者は画面から登録する）
    pw CONSTANT TEXT := '$2a$10$SR71e029/rhInSjaDZPBAeNe0J8i4m/uJv//DUZWZls8OGbueCOEq';
    sato BIGINT;
    suzuki BIGINT;
    tanaka BIGINT;
    suzuki_recent1 BIGINT;
    suzuki_recent2 BIGINT;
    sato_recent BIGINT;
    tanaka_liked BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE lower(username) IN ('sato_hana', 'suzuki_ken', 'tanaka_yu')) THEN
        RETURN;
    END IF;

    -- 利用者
    INSERT INTO users (username, display_name, email, password_hash, bio, last_timeline_viewed_at, created_at)
    VALUES ('sato_hana', '佐藤 花', 'sato@example.com', pw, 'カフェ巡りと写真が好きです。', now() - interval '7 hours',
            now() - interval '30 days')
    RETURNING id INTO sato;
    INSERT INTO users (username, display_name, email, password_hash, bio, created_at)
    VALUES ('suzuki_ken', '鈴木 健', 'suzuki@example.com', pw, 'エンジニア。週末は山に登ります。', now() - interval '20 days')
    RETURNING id INTO suzuki;
    INSERT INTO users (username, display_name, email, password_hash, bio, created_at)
    VALUES ('tanaka_yu', '田中 悠', 'tanaka@example.com', pw, '', now() - interval '10 days')
    RETURNING id INTO tanaka;

    -- フォロー
    INSERT INTO follows (follower_id, followee_id, created_at) VALUES
        (sato, suzuki, now() - interval '15 days'),
        (suzuki, sato, now() - interval '14 days'),
        (suzuki, tanaka, now() - interval '9 days'),
        (tanaka, sato, now() - interval '8 days');

    -- 田中の投稿 30 件（3 時間ごと。一番新しいものが 1 時間前）
    INSERT INTO posts (user_id, body, created_at, updated_at)
    SELECT tanaka, '田中の投稿 その' || i || '。今日も一日おつかれさまでした。',
           now() - interval '1 hour' - (i - 1) * interval '3 hours',
           now() - interval '1 hour' - (i - 1) * interval '3 hours'
    FROM generate_series(1, 30) AS i;
    SELECT id INTO tanaka_liked FROM posts WHERE user_id = tanaka AND body LIKE '田中の投稿 その5。%';

    -- 鈴木の投稿。2 件は佐藤の留守中（7 時間以内）で、反応がある（ハイライトに出る）
    INSERT INTO posts (user_id, body, created_at, updated_at)
    VALUES (suzuki, '今朝は高尾山に登ってきました。紅葉が始まっています。', now() - interval '5 hours', now() - interval '5 hours')
    RETURNING id INTO suzuki_recent1;
    INSERT INTO posts (user_id, body, created_at, updated_at)
    VALUES (suzuki, 'Spring Boot 4 のテスト、Testcontainers で本物の PostgreSQL を使うと安心感があります。',
            now() - interval '3 hours', now() - interval '3 hours')
    RETURNING id INTO suzuki_recent2;
    INSERT INTO posts (user_id, body, created_at, updated_at, edited_at) VALUES
        (suzuki, 'おはようございます。', now() - interval '2 days', now() - interval '2 days', NULL),
        (suzuki, '新しいキーボードを買いました。打ち心地がとても良いです。', now() - interval '3 days',
         now() - interval '3 days' + interval '10 minutes', now() - interval '3 days' + interval '10 minutes');

    -- 佐藤の投稿
    INSERT INTO posts (user_id, body, created_at, updated_at)
    VALUES (sato, '駅前に新しいカフェができていました☕ 今度行ってみます。', now() - interval '9 hours', now() - interval '9 hours')
    RETURNING id INTO sato_recent;
    INSERT INTO posts (user_id, body, created_at, updated_at) VALUES
        (sato, 'はじめまして、佐藤です。よろしくお願いします。', now() - interval '29 days', now() - interval '29 days'),
        (sato, '😀 絵文字も 1 文字として数えます。', now() - interval '4 days', now() - interval '4 days');

    -- いいね
    INSERT INTO likes (post_id, user_id, created_at) VALUES
        (suzuki_recent1, sato, now() - interval '4 hours'),
        (suzuki_recent1, tanaka, now() - interval '4 hours'),
        (suzuki_recent2, tanaka, now() - interval '2 hours'),
        (sato_recent, suzuki, now() - interval '8 hours'),
        (sato_recent, tanaka, now() - interval '8 hours'),
        -- 鈴木が田中の投稿にいいね。佐藤は田中をフォローしていないので、いいね経由で佐藤のタイムラインに出る
        (tanaka_liked, suzuki, now() - interval '30 minutes');

    -- コメント
    INSERT INTO comments (post_id, user_id, body, created_at, updated_at) VALUES
        (suzuki_recent1, tanaka, 'いいですね！写真も見たいです。', now() - interval '4 hours', now() - interval '4 hours'),
        (suzuki_recent1, suzuki, '次は写真も載せます。', now() - interval '3 hours 30 minutes',
         now() - interval '3 hours 30 minutes'),
        (sato_recent, suzuki, 'どんなお店か教えてください。', now() - interval '8 hours', now() - interval '8 hours');
END
$$;
