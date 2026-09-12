CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            TEXT         NOT NULL UNIQUE,
    password         TEXT,
    name             TEXT,
    is_confirmed     BOOLEAN      NOT NULL DEFAULT false,
    tokens_valid_from TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    last_login_at    TIMESTAMP
);

CREATE TABLE password_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token_blacklist (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti        TEXT         NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL
);

CREATE TABLE email_confirmation_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash TEXT         NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash TEXT         NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE login_attempts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         REFERENCES users(id) ON DELETE CASCADE,
    ip_address INET         NOT NULL,
    endpoint   TEXT         NOT NULL,
    failed_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_history_user ON password_history (user_id);
CREATE INDEX idx_refresh_blacklist_jti ON refresh_token_blacklist (jti);
CREATE INDEX idx_refresh_blacklist_user ON refresh_token_blacklist (user_id);
CREATE INDEX idx_email_confirmation_token ON email_confirmation_tokens (token_hash);
CREATE INDEX idx_email_confirmation_user ON email_confirmation_tokens (user_id);
CREATE INDEX idx_password_reset_token ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);
CREATE INDEX idx_login_attempts_user ON login_attempts (user_id);
CREATE INDEX idx_login_attempts_ip ON login_attempts (ip_address);
CREATE INDEX idx_login_attempts_endpoint_ip ON login_attempts (endpoint, ip_address);
