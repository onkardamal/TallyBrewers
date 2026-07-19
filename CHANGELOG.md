# Changelog

All notable changes to the SecureBank Passwordless Authentication project are documented in this file.

---

## [Phase 4: Production Readiness & Hardening] - 2026-07-19

### Added
- **Swagger / OpenAPI Documentation**: Integrated `springdoc-openapi` to automatically document all REST endpoints and render Swagger UI at `/swagger-ui/index.html`.
- **Database Cleanup Scheduler**: Implemented `DatabaseCleanupScheduler.java` running hourly background purges of expired session records and verification tokens to prevent database bloat.
- **Expiry Indexes**: Added Flyway migration `V4__add_expiry_indexes.sql` to optimize purge queries by indexing `expires_at` columns.
- **Frontend Polish**: Added "Copy to Clipboard" and "Download as .TXT" utility buttons to the `RecoveryCodes.tsx` screen to make saving recovery codes easy and safe.
- **Deployment Documentation**: Created `DEPLOYMENT.md` providing cloud deployment steps for Railway, Vercel, and Neon.
- **Aesthetic Refinement (Apple/Google Style)**: Configured Outfit & Plus Jakarta Sans typography, visual mesh gradients, custom animation transitions, and glassmorphic card layouts.
- **Auto-loading .env configurations**: Configured `AuthServiceApplication.java` to load and inject `.env` properties dynamically on startup from IDEs/CLI.
- **Memory Leak Fixes**: Added active cleanup of expired `FixedWindowRateLimiter` keys to prevent memory bloat.
- **Input Hardening**: Prevented SQL column truncation exceptions in `RequestContext` by truncating long headers.

### Refactored
- **Centralized Cookie Helper**: Extracted duplicate `refresh_token` ResponseCookie logic from `LoginController` and `RecoveryController` into `CookieHelper.java`.
- **JWT Verification Hardening**: Configured `JwtTokenProvider` to enforce strict verification of configured `issuer` and `audience` claims.
- **Global Rate Limiting**: Added rate-limiting coverage to the remaining authentication endpoints (`/register`, `/verify-email`, `/passkey/register/start`, `/passkey/register`).

---

## [Phase 3: Passkey Login & Account Recovery] - 2026-07-18

### Added
- **Passwordless WebAuthn Login**: Integrated Yubico assertion ceremonies (`/login/start` and `/login/verify`) to support biometric sign-in.
- **Rotating Refresh Tokens**: Session lifecycle backed by database session records mapping client IP addresses and User-Agent headers, with automatic rotation upon token refresh.
- **Account Recovery Flow**: Implemented `/recover/start`, `/recover/verify`, and `/recover/passkey` allowing device recovery via one-time recovery codes and email verification, revoking all existing credentials.
- **In-Memory Frontend Access Tokens**: Modified React frontend client to store JWT access tokens only in memory, fetching a new one automatically on `401 Unauthorized` responses by executing `/session/refresh`.
- **CSRF Protection**: Wired Spring Security's raw cookie-based CSRF filter (`CookieCsrfTokenRepository` + raw request attribute handler).

---

## [Phase 2: User Onboarding & Email Verification] - 2026-07-17

### Added
- **Flyway Database Schema**: Initialized schema and WebAuthn user handle fields (`V1__init_schema.sql` and `V2__add_webauthn_user_handle.sql`).
- **Secure Email Verification**: Created Single-use verification tokens stored as SHA-256 hashes, with resending throttled.
- **WebAuthn Registration Ceremony**: Setup Yubico RelyingParty for creation ceremonies (`/passkey/register/start` and `/passkey/register`).
- **Cryptographic Recovery Codes**: Generated 10 high-entropy recovery codes stored as secure BCrypt hashes.

---

## [Phase 1: Basic Structure] - 2026-07-16

### Added
- **Spring Boot Skeleton**: Initialized package layout following clean architecture patterns.
- **React Frontend**: Implemented basic SPA structure with Vite and mock page routes.
