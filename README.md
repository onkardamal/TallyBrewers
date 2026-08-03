# SecureBank Passwordless Authentication Service

SecureBank is a state-of-the-art passwordless authentication system designed for high-security banking applications. Built using a **clean architecture package design** and **state-free session lifecycle management**, the system leverages the **WebAuthn / Passkey** standard to completely eliminate password-related attack vectors.

---

## 1. Document Directory Map

Review the dedicated specifications in this repository for full engineering details:
- [SYSTEM_ARCHITECTURE.md](file:///c:/TallyBrewers/SYSTEM_ARCHITECTURE.md): Class layout, package boundaries, and component layout.
- [DATA_FLOW.md](file:///c:/TallyBrewers/DATA_FLOW.md): Mermaid sequence diagrams mapping registration, sign-in, session refresh, and recovery.
- [API.md](file:///c:/TallyBrewers/API.md): Detailed specifications of payload formats, headers, and status codes.
- [DATABASE.md](file:///c:/TallyBrewers/DATABASE.md): ERD structure, constraints, index mapping, and cleanup rules.
- [SECURITY.md](file:///c:/TallyBrewers/SECURITY.md): Threat mitigation model, CSRF/XSRF configs, and encryption layers.
- [DEPLOYMENT.md](file:///c:/TallyBrewers/DEPLOYMENT.md): Railway, Vercel, and Neon cloud instructions.
- [CHANGELOG.md](file:///c:/TallyBrewers/CHANGELOG.md): Record of changes and phase accomplishments.

---

## 2. Core Functional Features

- **Passwordless WebAuthn Onboarding**: Complete attestation verification using the Yubico WebAuthn server library.
- **Biometric WebAuthn Login**: Signed challenge assertion ceremonies via browser biometrics.
- **State-free Session Lifecycle**: Short-lived (15 min) JWT access tokens combined with stateful rotating refresh tokens in HTTP-only, secure, SameSite=Lax cookies.
- **One-time Account Recovery**: Account passkey reset using email validation + one of 10 cryptographically random BCrypt-hashed recovery codes, invalidating prior credentials.
- **Raw Cookie CSRF Protection**: Raw token verification via JS header echoing.
- **IP-based Rate Limiting**: All authentication endpoints throttled to 1 request per 60 seconds per IP.
- **Hourly Purge Scheduler**: Automatic background purge of expired sessions and verification tokens.
- **OpenAPI Swagger UI**: Self-documenting controllers accessible at `/swagger-ui/index.html`.
- **Hybrid Mail Sender Subsystem**: Dual-mode transactional email delivery:
  - **SMTP Mode** (Default): Outbound mail sending using standard SMTP servers (Gmail, Mailtrap, local Mailpit).
  - **HTTP API Mode**: Direct HTTPS requests via Brevo REST API to bypass firewall-level SMTP port blocking on cloud hosting providers (e.g., Railway, Render).

---

## 3. Local Development Setup

### Prerequisites
- **Java 21+** and the Maven Wrapper (`auth-service/mvnw`)
- **Node.js 20+** and npm (for React)
- **PostgreSQL 17** running locally with:
  - database `securebank` owned by role `securebank_app`
  - database `securebank_test` (for JUnit integration tests)
- **Mailpit SMTP Catcher** (`winget install axllent.mailpit` or run locally) for email link capture.

### Step 1: Configuration (.env)
1. Copy `auth-service/.env.example` to `auth-service/.env`:
   ```bash
   cp auth-service/.env.example auth-service/.env
   ```
2. Fill in the database credentials, SMTP hosts, and JWT secrets in `auth-service/.env`.

### Step 2: Start Services

1. **Start SMTP Catcher**:
   ```bash
   mailpit
   ```
   Web UI: http://localhost:8025

2. **Start Backend Service**:
   ```bash
   cd auth-service
   # Ensure environment variables from .env are loaded, then run:
   ./mvnw spring-boot:run
   ```
   API Root: http://localhost:8080
   Swagger UI: http://localhost:8080/swagger-ui/index.html

3. **Start Frontend Client**:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   SPA: http://localhost:5173

---

## 4. Run Test Suite

### Prerequisites
- **PostgreSQL 17** running locally (or in Docker) with a dedicated database called `securebank_test`.
- The local tests connect using the default credential values of `securebank_app` / `your_local_postgres_password` (customizable via `TEST_DB_PASSWORD` or `TEST_DB_USERNAME`).

### Run Instructions
To execute the backend test suite:
```bash
cd auth-service
./mvnw test
```

### Expected Startup Behavior
1. **Docker Container Launch**: The test suite uses **Testcontainers** to orchestrate PostgreSQL automatically if Docker is active. If Docker is not available, it automatically falls back to your local Windows PostgreSQL instance.
2. **Flyway Migrations**: Flyway runs automatically on startup against this container, executing all migrations (`V1` through `V4`) to prepare the database schema.
3. **GreenMail**: An in-process GreenMail server is started locally for SMTP assertions.
4. **Clean State**: Before each test method, the tables are automatically truncated, ensuring completely reproducible, isolated test runs.
5. **Port mapping**: The dynamic PostgreSQL container maps its port randomly, ensuring no port conflicts with any locally running PostgreSQL instance.

---

## 5. Production Cloud Deployment Config

To connect your **Vercel React Frontend**, **Railway Spring Boot Backend**, and **Neon PostgreSQL Database**, configure the following environment variables on Railway:

| Environment Variable | Description | Recommended Value |
| --- | --- | --- |
| **DB_HOST** | Neon DB host domain | `your_neon_db_host` |
| **DB_PORT** | Database connection port | `5432` |
| **DB_NAME** | Database schema query string | `neondb?sslmode=require` |
| **DB_USERNAME** | Database username | `your_neon_db_username` |
| **DB_PASSWORD** | Neon database user password | `your_neon_db_password` |
| **SECUREBANK_MAIL_PROVIDER** | Toggle email mode (SMTP or REST) | `brevo-http` |
| **BREVO_API_KEY** | Brevo v3 REST API Key | `your_brevo_api_key` |
| **MAIL_FROM** | Verified Brevo sender address | `your_verified_sender_email` |
| **WEBAUTHN_RP_ID** | Passkey Relying Party Domain ID | `your_vercel_frontend_domain` |
| **WEBAUTHN_RP_NAME** | App Display Name for Passkeys | `SecureBank` |
| **WEBAUTHN_ORIGIN** | Allowed Frontend CORS Domain | `https://your_vercel_frontend_domain` |
| **MAIL_VERIFICATION_URL_BASE** | Frontend email validation path | `https://your_vercel_frontend_domain/verify-email` |

---

## 6. Deployed Environment Reference

The production environment is live and accessible at the following public endpoints:

* **Frontend Client (Vercel)**: [https://securebank-pink.vercel.app](https://securebank-pink.vercel.app)
* **Backend REST API (Railway)**: [https://tallybrewers-production.up.railway.app](https://tallybrewers-production.up.railway.app)
* **Database (Neon)**: Host `ep-plain-heart-axgkivhw.c-4.us-east-2.aws.neon.tech` (schema: `neondb`)

