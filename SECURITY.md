# Security Architecture & Threat Model

This document outlines the security controls, cryptographic safeguards, threat mitigation strategies, and design decisions implemented to protect the SecureBank passwordless authentication service.

---

## 1. Cryptographic Safeguards & Data Storage

| Data Asset | Transit Protocol | Storage Strategy | Cryptographic Algorithm |
| :--- | :--- | :--- | :--- |
| **Passkey Private Keys** | Never leaves device | Never transmitted / stored | ECDSA / RSA (Device-owned) |
| **Passkey Public Keys** | HTTPS API Payload | Plaintext Database Field | COSE Key Structure (Yubico verified) |
| **Recovery Codes** | HTTPS API Payload (Once) | BCrypt Hash in Database | BCrypt (Cost: 10) |
| **Email Verification Tokens** | Email Link Url Parameter | SHA-256 Hash in Database | SHA-256 |
| **Access Tokens** | Authorization Header | In-Memory (Client) | HMAC-SHA256 Signed JWT |
| **Refresh Tokens** | HTTP-Only Cookie | SHA-256 Hash in Database | SHA-256 |

### Passkey Secrets (WebAuthn)
SecureBank uses the WebAuthn standard for authentication. The client device generates an asymmetric key pair. The private key remains within the device's hardware security module (Secure Enclave / TPM), while the public key is stored in the PostgreSQL database. The backend validates challenges signed by the private key, ensuring no credentials or passwords can be leaked or stolen from the server.

### Recovery Code Hashing
To prevent compromised database backups from yielding access to accounts, the 10 random recovery codes are shown to the user exactly once in plaintext. The database only stores their **BCrypt hashes** (via Spring Security's `BCryptPasswordEncoder`).

### Email Verification Token Hashing
Verification tokens delivered via email links are high-entropy UUIDs. They are hashed using SHA-256 before being saved to the database. Even if the database is read-compromised, an attacker cannot extract active tokens from the records to activate accounts.

---

## 2. Session Lifecycle & Token Hardening

### Stateful Refresh Token Rotation (RTR)
- **Rotation**: Every time `/session/refresh` is called, the old refresh token is deleted, a new refresh token is generated, and a new HTTP-Only cookie is sent to the browser.
- **Concurrent Session Locking**: Refresh tokens are mapped to active sessions in the database containing the client's `ipAddress` and `userAgent`. This enables active session tracking, remote logout, and concurrent session limits.
- **Replay Protection**: If an attacker attempts to replay an already-consumed refresh token, the transaction will fail, and the session record will be immediately deleted.

### Access Token (JWT) Hardening
- **Strict Scope Validation**: Configured `JwtTokenProvider` to sign tokens and require the `Issuer` and `Audience` claims during verification, preventing token substitution attacks.
- **Client Storage**: The JWT access token is stored **strictly in application memory** (React state). It is never saved to `localStorage` or `sessionStorage`, making it completely immune to Cross-Site Scripting (XSS) token extraction.

### Cookie Configuration
The `refresh_token` cookie is locked down with strict browser attributes:
- `HttpOnly`: Prevents JavaScript execution scripts from reading the cookie, mitigating XSS theft.
- `Secure`: Forces the browser to send the cookie over encrypted HTTPS connections only (enforced automatically in browsers for localhost and production domains).
- `SameSite=Lax`: Restricts cookie transmission on cross-site requests, mitigating Cross-Site Request Forgery (CSRF).

---

## 3. Defense-in-Depth Mitigations

### CSRF Protection (Spring Security Raw Cookie Flow)
For cookie-based mutating requests (POST `/logout` and `/session/refresh`), Spring Security's raw cookie-based CSRF protection is active:
1. The backend issues a raw `XSRF-TOKEN` cookie.
2. The React client reads the cookie value via JavaScript and attaches it to the custom header `X-XSRF-TOKEN` on all mutating requests.
3. Spring Security validates the header matches the cookie token before routing to endpoints.

### Rate Limiting
All authentication endpoints are protected against abuse and brute-force attacks via the `FixedWindowRateLimiter`:
- **Window**: 1 request per 60 seconds per client IP key.
- **Endpoints Covered**: `/register`, `/verify-email`, `/verify-email/resend`, `/passkey/register/start`, `/passkey/register`, `/login/start`, `/login/verify`, `/session/refresh`, `/recover/start`, `/recover/verify`, `/recover/passkey`.

### Account Enumeration Protection
All user-facing bootstrap endpoints (`/register`, `/verify-email/resend`, `/recover/start`) return **generic, identical success responses** regardless of whether the email address exists in the system or is already verified.