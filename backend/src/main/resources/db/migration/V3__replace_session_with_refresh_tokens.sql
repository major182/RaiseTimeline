-- 認証をセッション方式から JWT 方式に変えた（技術選定書 4.1）。
-- Spring Session のテーブルをやめ、リフレッシュトークンのテーブルを作る（DB 設計書 4.7）
DROP TABLE spring_session_attributes;
DROP TABLE spring_session;

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 元の値ではなく SHA-256 のハッシュ（16 進数 64 文字）だけを保存する（DB 設計書 D-9）
    token_hash TEXT        NOT NULL CONSTRAINT refresh_tokens_token_hash_check CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    -- 無効にした理由。ROTATED（取り直しで使い終わった）のものが再び使われたときだけ、盗まれたとみなす
    revoke_reason TEXT CONSTRAINT refresh_tokens_revoke_reason_check
        CHECK (revoke_reason IN ('ROTATED', 'LOGOUT', 'PASSWORD_CHANGED', 'REUSE_DETECTED')),
    CONSTRAINT refresh_tokens_revoked_check CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX refresh_tokens_token_hash_key ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
