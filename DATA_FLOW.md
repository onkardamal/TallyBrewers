# Data Flow Sequences

This document illustrates the precise sequence of operations and handshakes between the User, React Frontend SPA, Spring Boot Backend, Database, and Mail Server.

---

## 1. User Registration & Passkey Setup

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Browser
    participant SPA as React Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant Mail as Mail Server

    User->>Browser: Fill Name & Email, click Register
    Browser->>SPA: Submit Registration
    SPA->>API: POST /register {name, email, phone}
    API->>DB: Create User (status: PENDING_VERIFICATION)
    API->>DB: Save verification token hash (SHA-256)
    API->>Mail: Send verification email with raw token
    API-->>SPA: 201 Created (generic success message)
    SPA-->>User: Display check inbox screen

    Note over User, Mail: Email Verification Link Clicked
    User->>Browser: Click link (https://.../verify-email?token=xyz)
    Browser->>SPA: Load page, fetch token parameter
    SPA->>API: POST /verify-email {token}
    API->>DB: Match hash, check expiry & single-use
    API->>DB: Mark token used, update User (status: VERIFIED)
    API-->>SPA: 200 OK (Email verified)
    SPA-->>User: Prompt to create passkey

    Note over User, Mail: WebAuthn Passkey Registration
    User->>SPA: Click "Create Passkey"
    SPA->>API: POST /passkey/register/start {email}
    API->>DB: Verify status is VERIFIED
    API->>API: Generate PublicKeyCredentialCreationOptions & Handle
    API->>DB: Cache challenge handle in ChallengeStore
    API-->>SPA: 200 OK {handle, creationOptions}
    SPA->>Browser: navigator.credentials.create(creationOptions)
    Note over Browser: Browser biometric popup
    Browser-->>SPA: Return PublicKeyCredential attestation
    SPA->>API: POST /passkey/register {handle, credential}
    API->>API: Retrieve challenge from ChallengeStore, verify signature
    API->>DB: Save Passkey public key & credential ID
    API->>API: Generate 10 recovery codes (BCrypt hashed)
    API->>DB: Save recovery code hashes
    API->>DB: Update User (status: ACTIVE)
    API-->>SPA: 200 OK {recoveryCodes}
    SPA-->>User: Display recovery codes once
```

---

## 2. Passkey Authentication (Login)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Browser
    participant SPA as React Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL

    User->>Browser: Enter email, click Sign In
    Browser->>SPA: Submit email
    SPA->>API: POST /login/start {email}
    API->>DB: Verify User status is ACTIVE
    API->>API: Generate PublicKeyCredentialRequestOptions & Handle
    API->>DB: Cache challenge handle in ChallengeStore
    API-->>SPA: 200 OK {handle, assertionOptionsJson}
    SPA->>Browser: navigator.credentials.get(assertionOptions)
    Note over Browser: Browser biometrics popup
    Browser-->>SPA: Return PublicKeyCredential assertion
    SPA->>API: POST /login/verify {handle, credential}
    API->>API: Retrieve challenge, verify WebAuthn assertion signature
    API->>API: Generate access token (JWT) & refresh token (random)
    API->>DB: Save session record (hash, IP, User-Agent)
    API-->>SPA: 200 OK {accessToken} + HTTP-Only Set-Cookie: refresh_token
    SPA->>SPA: Store accessToken in memory
    SPA->>SPA: Navigate to /dashboard
    SPA-->>User: Render Dashboard
```

---

## 3. Session Refresh (Automatic Retry Interceptor)

```mermaid
sequenceDiagram
    autonumber
    participant SPA as React Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL

    SPA->>API: GET /me (Authorization: Bearer <expired-token>)
    API->>API: JwtAuthenticationFilter detects expired token
    API-->>SPA: 410 Unauthorized (or 401)
    
    Note over SPA, API: HTTP Interceptor catches 401
    SPA->>API: POST /session/refresh (Cookie: refresh_token)
    API->>DB: Fetch session by refresh token hash, verify valid
    API->>API: Generate new Access Token & rotating Refresh Token
    API->>DB: Delete old session record, save new session record
    API-->>SPA: 200 OK {accessToken} + HTTP-Only Set-Cookie: new_refresh_token
    SPA->>SPA: Store new accessToken in memory
    
    Note over SPA, API: Retry initial request
    SPA->>API: GET /me (Authorization: Bearer <new-token>)
    API-->>SPA: 200 OK {userProfile}
```

---

## 4. Account Recovery & Passkey Reset

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Browser
    participant SPA as React Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant Mail as Mail Server

    User->>Browser: Enter email on Recovery Page
    Browser->>SPA: Submit
    SPA->>API: POST /recover/start {email}
    API->>DB: Check user status is ACTIVE
    API->>DB: Generate recovery verification token, store hash (SHA-256)
    API->>Mail: Send recovery email with token link
    API-->>SPA: 200 OK (Generic link sent message)
    SPA-->>User: Display inbox check message

    Note over User, Mail: Link Clicked & Recovery Verified
    User->>Browser: Click link (https://.../recover?token=xyz)
    Browser->>SPA: Load recovery form
    User->>SPA: Fill Email, Recovery Code (e.g. Code 1)
    SPA->>API: POST /recover/verify {email, token, recoveryCode}
    API->>DB: Verify email token hash (valid, not expired)
    API->>DB: Fetch user recovery code hashes, verify match (BCrypt)
    API->>DB: Mark recovery code as USED
    API->>API: Initiate WebAuthn setup: generate creation options
    API->>DB: Cache challenge handle in ChallengeStore
    API-->>SPA: 200 OK {handle, creationOptions}
    
    SPA->>Browser: navigator.credentials.create(creationOptions)
    Note over Browser: Browser biometrics popup
    Browser-->>SPA: Return new PublicKeyCredential attestation
    SPA->>API: POST /recover/passkey {handle, credential}
    API->>API: Verify challenge & credential attestation signature
    API->>DB: Delete ALL old passkeys for the user (revocation)
    API->>DB: Save new passkey public key
    API->>DB: Delete ALL old recovery codes, save 10 new hashes
    API->>API: Generate access token & session
    API-->>SPA: 200 OK {accessToken, recoveryCodes} + HTTP-Only Set-Cookie
    SPA->>SPA: Store new accessToken in memory
    SPA->>SPA: Redirect to /recovery-codes (display new 10 codes)
    SPA-->>User: Show new recovery codes
```