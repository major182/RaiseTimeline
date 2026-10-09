-- 利用者（DB 設計書 4.1）

-- 部分一致の検索を速くする拡張機能（DB 設計書 5.6）。RDS でも使える
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username                TEXT        NOT NULL CONSTRAINT users_username_check CHECK (username ~ '^[A-Za-z0-9_]{4,15}$'),
    display_name            TEXT        NOT NULL CONSTRAINT users_display_name_check CHECK (char_length(display_name) BETWEEN 1 AND 50),
    email                   TEXT        NOT NULL CONSTRAINT users_email_check CHECK (char_length(email) <= 254),
    password_hash           TEXT        NOT NULL,
    bio                     TEXT        NOT NULL DEFAULT '' CONSTRAINT users_bio_check CHECK (char_length(bio) <= 160),
    avatar_key              TEXT,
    last_timeline_viewed_at TIMESTAMPTZ,
    failed_login_count      INTEGER     NOT NULL DEFAULT 0 CONSTRAINT users_failed_login_count_check CHECK (failed_login_count >= 0),
    locked_until            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 大文字・小文字を区別せずに重複を禁止する（BR-02・BR-04、DB 設計書 D-8）
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

-- 利用者の検索（部分一致）用
CREATE INDEX users_username_trgm_idx ON users USING gin (lower(username) gin_trgm_ops);
CREATE INDEX users_display_name_trgm_idx ON users USING gin (lower(display_name) gin_trgm_ops);
