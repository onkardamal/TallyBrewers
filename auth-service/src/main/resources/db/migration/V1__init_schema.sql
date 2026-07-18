-- V1__init_schema.sql
--
-- Initial schema for the SecureBank passwordless authentication system.
-- Tables and columns here reflect DATABASE.md plus the additions approved
-- during architecture review:
--   - recovery_codes table (was missing from the original DATABASE.md draft)
--   - email_verifications table (dedicated table instead of columns on users)
--   - created_at timestamps added to passkeys / recovery_codes / email_verifications
--
-- No application logic is implemented yet. This migration only establishes
-- the schema so later phases can build on top of it incrementally.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255)        NOT NULL,
    email         VARCHAR(320)        NOT NULL,
    phone         VARCHAR(32),
    status        VARCHAR(32)         NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at    TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE passkeys (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT              NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    credential_id   VARCHAR(512)        NOT NULL,
    public_key      TEXT                NOT NULL,
    counter         BIGINT              NOT NULL DEFAULT 0,
    device_name     VARCHAR(255),
    last_used       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CONSTRAINT uq_passkeys_credential_id UNIQUE (credential_id)
);

CREATE INDEX idx_passkeys_user_id ON passkeys (user_id);

CREATE TABLE sessions (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT              NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash    VARCHAR(255)        NOT NULL,
    created_at            TIMESTAMPTZ         NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ         NOT NULL,
    last_activity         TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CONSTRAINT uq_sessions_refresh_token_hash UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_sessions_user_id ON sessions (user_id);

CREATE TABLE recovery_codes (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT              NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash     VARCHAR(255)        NOT NULL,
    used          BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CONSTRAINT uq_recovery_codes_code_hash UNIQUE (code_hash)
);

CREATE INDEX idx_recovery_codes_user_id ON recovery_codes (user_id);

CREATE TABLE email_verifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT              NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash    VARCHAR(255)        NOT NULL,
    expires_at    TIMESTAMPTZ         NOT NULL,
    verified      BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ         NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_verifications_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verifications_user_id ON email_verifications (user_id);

CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT              REFERENCES users (id) ON DELETE SET NULL,
    event         VARCHAR(100)        NOT NULL,
    ip_address    VARCHAR(45),
    device        VARCHAR(255),
    timestamp     TIMESTAMPTZ         NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp);
