# System Architecture

Frontend

↓

Authentication API

↓

Authentication Service

↓

Session Service

↓

Database

Database Tables

users

passkeys

sessions

recovery_codes

email_verifications

audit_logs

No additional services.

No microservices.

Single clean backend.

## Backend package structure (Clean Architecture)

auth-service/src/main/java/com/securebank/auth/

- api/            REST controllers (thin, no business logic)
- application/     Services orchestrating use cases (registration, login, recovery, sessions)
- domain/          Entities and core domain logic
- infrastructure/  Persistence (JPA repositories), WebAuthn integration, JWT signing, email sending
- security/        Spring Security building blocks (filters, WebAuthn glue)
- config/          Spring Boot configuration (SecurityConfig, typed properties)

Database schema is owned exclusively by Flyway migrations
(src/main/resources/db/migration). Hibernate/JPA never auto-generates
or alters schema (`ddl-auto: validate`).

## Frontend structure

frontend/src/

- pages/       One component per screen in the auth flow (Login, Register,
  VerifyEmail, PasskeySetup, RecoveryCodes, Recover, Dashboard)
- components/  Shared/reusable UI components
- api/         HTTP client and API request functions

Login is the first screen (route "/"), per UI_GUIDELINES.md.
Styling uses TailwindCSS utility classes exclusively.

## Local development stack (Phase 1)

- Backend: Spring Boot 3.5.16, Java 21, Maven (via Maven Wrapper)
- Database: PostgreSQL 17, local instance, dedicated `securebank_app` role
  (not superuser) owns the `securebank` database
- Frontend: React 19 + TypeScript, Vite, TailwindCSS v4, react-router-dom
- Email: Spring Mail (SMTP), abstracted behind an interface for later
  provider swaps (SendGrid, AWS SES, etc.)
- WebAuthn Relying Party ID/origin: configurable via application
  properties, defaults to localhost for development

## Phase 2 building blocks

WebAuthn (passkey registration):

- Library: Yubico java-webauthn-server (`com.yubico:webauthn-server-core`
  2.9.0). Performs all real attestation verification.
- `RelyingParty` bean built from configuration (RP id / name / origin), so
  dev→prod is a config change only.
- `JpaCredentialRepository` adapts our persistence layer to the library's
  `CredentialRepository` interface.
- Registration is a two-step ceremony: `/passkey/register/start` (begin) and
  `/passkey/register` (finish). The pending challenge is held server-side
  behind the `ChallengeStore` interface — never trusted from the client.
  - `ChallengeStore<T>` interface (infrastructure/webauthn) with an in-memory,
    single-node implementation (`InMemoryChallengeStore`, 5-min TTL,
    single-use). Swap for Redis in a scaled deployment without touching
    business logic.
- Stable opaque WebAuthn user handle: a random UUID stored on the user
  (`users.webauthn_user_handle`, Flyway V2), used as WebAuthn `user.id`
  instead of the numeric PK or the email.

Email:

- `EmailSender` interface (application) with an SMTP implementation
  (`SmtpEmailSender`, infrastructure/email). Verification links are built from
  a configurable frontend base URL.

Cryptography:

- Email verification tokens: high-entropy random tokens, stored as SHA-256
  hashes (`TokenHasher`). Raw token only ever leaves via email.
- Recovery codes: cryptographically random, stored as BCrypt hashes
  (`RecoveryCodeService`), shown in plaintext exactly once.

Rate limiting:

- `RateLimiter` interface (application) with an in-memory fixed-window
  implementation (`FixedWindowRateLimiter`, infrastructure/ratelimit). Applied
  to `/verify-email/resend` (1 per 60s per email).

Audit logging:

- `AuditService` records security-relevant events to `audit_logs`.

Cross-cutting API concerns:

- `GlobalExceptionHandler` maps application/validation errors to safe,
  generic HTTP responses (no internal detail, no account enumeration).

## Testing approach (Phase 2)

- Integration tests run against a REAL PostgreSQL 17 engine and a REAL
  in-process SMTP server (GreenMail) — no H2, no mocked mail.
- WebAuthn is tested with a REAL software authenticator (genuine EC P-256
  keys + CBOR/COSE encoding) so the Yubico library performs actual
  cryptographic verification. There is no mocked/bypassed authentication.
- Provisioning note: Testcontainers was the original plan, but Docker is not
  installed on this development machine, so integration tests use a dedicated
  `securebank_test` database on the local PostgreSQL instance instead. This
  preserves the "real database engine" guarantee. Testcontainers dependencies
  remain in the POM for CI environments that provide Docker.