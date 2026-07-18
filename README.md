# SecureBank Authentication

Passwordless (WebAuthn/passkey) authentication for a banking application.
See the design docs in this folder: Project Brief.md, SYSTEM_ARCHITECTURE.md,
DATABASE.md, DATA_FLOW.md, API.md, SECURITY.md, UI_GUIDELINES.md.

## Prerequisites

- Java 21+ and the bundled Maven Wrapper (`auth-service/mvnw`)
- Node.js + npm (frontend)
- PostgreSQL 17 running locally with:
  - database `securebank` owned by role `securebank_app`
  - database `securebank_test` (for integration tests)
- A local SMTP catcher for development email — Mailpit is recommended
  (`winget install axllent.mailpit`). Production uses real SMTP.

## Configuration

Backend config is environment-driven. Copy `auth-service/.env.example` to
`auth-service/.env` and fill in values (the `.env` file is gitignored and must
never be committed). Key groups: database, SMTP, WebAuthn RP, JWT.

Frontend config: `frontend/.env` sets `VITE_API_BASE_URL` (defaults to
`http://localhost:8080`).

## Running locally (registration flow)

1. Start the SMTP catcher:

   ```
   mailpit
   ```
   Web UI (view captured mail): http://localhost:8025

2. Start the backend (loads `.env`, then runs Spring Boot):

   ```
   cd auth-service
   # load .env into the environment, then:
   ./mvnw spring-boot:run
   ```
   Backend: http://localhost:8080

   Note: if port 8080 is already in use, set `SERVER_PORT` (and update the
   frontend `VITE_API_BASE_URL` to match).

3. Start the frontend:

   ```
   cd frontend
   npm install
   npm run dev
   ```
   App: http://localhost:5173

4. Walk the flow in the browser:
   `/register` → check Mailpit (http://localhost:8025) for the email → open the
   verification link (`/verify-email?token=...`) → `/passkey-setup` → create a
   passkey with your platform authenticator → save the recovery codes.

## Tests

```
cd auth-service
./mvnw test
```

Integration tests run against the real local PostgreSQL (`securebank_test`),
a real in-process SMTP server (GreenMail), and real WebAuthn verification via
a software authenticator. No mocked authentication.
