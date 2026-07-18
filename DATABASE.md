# Database

users

id

name

email

phone

status

webauthn_user_handle   (UUID, opaque WebAuthn user.id — added in Flyway V2)

created_at

----------------------

passkeys

id

user_id

credential_id

public_key

counter

device_name

last_used

created_at

----------------------

sessions

id

user_id

refresh_token_hash

created_at

expires_at

last_activity

----------------------

recovery_codes

id

user_id

code_hash

used

created_at

----------------------

email_verifications

id

user_id

token_hash

expires_at

verified

created_at

----------------------

audit_logs

id

user_id

event

ip_address

device

timestamp

----------------------

Notes:

- recovery_codes and email_verifications were added during architecture
  review (approved) to support the recovery code and email verification
  flows described in Project Brief.md and DATA_FLOW.md. They were missing
  from the original schema draft above.
- created_at was added to passkeys, recovery_codes, and email_verifications
  for auditability.
- Schema is created and versioned exclusively via Flyway migrations
  (see auth-service/src/main/resources/db/migration). JPA/Hibernate does
  not auto-generate or alter the schema.
- V2 migration adds `users.webauthn_user_handle` (UUID, unique): a stable,
  opaque per-user identifier used as the WebAuthn user.id, rather than the
  numeric primary key (enumeration risk) or the email (PII / mutable).