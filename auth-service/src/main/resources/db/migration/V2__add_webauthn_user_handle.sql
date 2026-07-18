-- V2__add_webauthn_user_handle.sql
--
-- Adds a stable, opaque WebAuthn user handle to each user.
--
-- The WebAuthn spec (user.id in PublicKeyCredentialCreationOptions) requires
-- an opaque byte identifier for the user that is NOT the numeric primary key
-- (which could leak enumeration information) and NOT PII like the email
-- address (which could change). We use a random UUID, generated once at
-- registration time and never changed afterward, per Phase 2 architecture
-- review.

ALTER TABLE users
    ADD COLUMN webauthn_user_handle UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX uq_users_webauthn_user_handle ON users (webauthn_user_handle);

-- Drop the DEFAULT after backfilling existing rows (none exist yet in any
-- real environment at this phase, but this keeps the column's default
-- generation explicit to application code going forward rather than the
-- database, matching how every other identifier in this schema is created).
ALTER TABLE users
    ALTER COLUMN webauthn_user_handle DROP DEFAULT;
