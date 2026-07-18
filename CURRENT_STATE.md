# Current State

Backend

50% (registration, email verification + resend, WebAuthn passkey registration,
recovery codes — implemented, integration-tested, and verified end-to-end
against real infra. Robustness fixes: graceful malformed-JSON and mail-failure
handling, /error no longer masked as 403, default Spring Security user removed.
Login, session management, and recovery flows still pending.)

Frontend

45% (registration flow fully wired to the backend: Register, VerifyEmail
(with resend), PasskeySetup (real browser WebAuthn), RecoveryCodes; shared
AuthLayout/Button/TextField/Alert components; typed API client. Login is a
Phase 3 placeholder; recovery UI pending.)

Database

100% (schema via Flyway V1 + V2)

Authentication

45% (full passwordless onboarding path complete and browser-wired:
registration → email verification → passkey registration → recovery codes.
Login/assertion not yet implemented.)

Current Task

Frontend connected to registration endpoints; default security user removed;
passkey entity fields verified; docs updated. Registration flow verified
end-to-end against real PostgreSQL + real SMTP (Mailpit) + real WebAuthn
options. Final human browser click-through (with a platform authenticator)
recommended before/at Phase 3 start.

Next

Phase 3: Login (WebAuthn assertion) + session management (JWT access token +
rotating refresh token in HttpOnly cookie).
