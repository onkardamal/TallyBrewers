-- V4__add_expiry_indexes.sql
--
-- Adds indexes to sessions(expires_at) and email_verifications(expires_at)
-- to optimize scheduled hourly purges of expired session and verification records.

CREATE INDEX idx_sessions_expires_at ON sessions (expires_at);
CREATE INDEX idx_email_verifications_expires_at ON email_verifications (expires_at);
