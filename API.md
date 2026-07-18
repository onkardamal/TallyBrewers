# SecureBank HTTP API Specifications

This document defines the complete REST API contract for the SecureBank passwordless authentication service. All requests and responses are exchanged using the `application/json` format (unless specified otherwise).

---

## 1. Authentication Status Codes & Error Format

Errors return standard HTTP status codes. The response body is serialized as:
```json
{
  "message": "Human-readable error description, suitable for client display."
}
```

### Standard Status Codes:
- `200 OK` / `201 Created`: Operation succeeded.
- `400 Bad Request`: Input validation failed, or WebAuthn signature verification failed, or token was invalid/expired.
- `401 Unauthorized`: Missing or invalid JWT access token on secure endpoints, or invalid/expired session cookie on refresh.
- `429 Too Many Requests`: Client IP throttled by the fixed-window rate limiter (60-second window).
- `500 Internal Server Error`: Generic unhandled backend failure.

---

## 2. Public Registration Endpoints

### POST /register
Creates a user pending verification. Sends an activation email.
- **Request Headers**: None
- **Request Payload**:
  ```json
  {
    "name": "Jane Doe",
    "email": "jane@example.com",
    "phone": "+15550199" // Optional
  }
  ```
- **Response**: `201 Created`
  ```json
  {
    "message": "If the details are valid, a verification email has been sent."
  }
  ```
- **Enumeration Protection**: Returns the same success message regardless of whether the email address was already registered.

### POST /verify-email
Activates the email address.
- **Request Payload**:
  ```json
  {
    "token": "79b884db-40a1-43e6-9907-f27718e801cd"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "message": "Email verified. You can now set up your passkey."
  }
  ```

### POST /verify-email/resend
Resends the activation link to the user's inbox, invalidating older links.
- **Request Payload**:
  ```json
  {
    "email": "jane@example.com"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "message": "If the account exists and is unverified, a new verification email has been sent."
  }
  ```
- **Rate Limiting**: Enforces a 60-second cooldown per IP. Excess requests return `429 Too Many Requests`.

---

## 3. Public Passkey Registration (Ceremony)

### POST /passkey/register/start
Begins the WebAuthn creation ceremony.
- **Request Payload**:
  ```json
  {
    "email": "jane@example.com"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "handle": "b328a9ea-96b6-4dfb-9ef1-f9f257adfb2e",
    "creationOptions": {
      "publicKey": {
        "rp": { "name": "SecureBank", "id": "localhost" },
        "user": {
          "name": "jane@example.com",
          "displayName": "Jane Doe",
          "id": "YWxpY2UtYWRhbXMtYWNjb3VudC1oYW5kbGU" // Base64URL UUID
        },
        "challenge": "a1b2c3d4...",
        "pubKeyCredParams": [ { "type": "public-key", "alg": -7 } ]
      }
    }
  }
  ```

### POST /passkey/register
Completes passkey setup.
- **Request Payload**:
  ```json
  {
    "handle": "b328a9ea-96b6-4dfb-9ef1-f9f257adfb2e",
    "credential": { ...browser PublicKeyCredential serialized response... }
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "recoveryCodes": [
      "QT88E-AH7AM",
      "4JPNS-25DPJ",
      "83W3K-YVPYA",
      "RCAJF-A65RF",
      "KWFK7-6EU37",
      "4TMBS-QPFF6",
      "3YGC4-DKJMM",
      "N2FJB-PKFHF",
      "779XB-XFHBR",
      "A94PE-FD6SF"
    ]
  }
  ```
- **Note**: Plaintext recovery codes are returned **only once** on the first passkey setup.

---

## 4. Authentication & Session Endpoints

### POST /login/start
Begins the passkey assertion ceremony.
- **Request Payload**:
  ```json
  {
    "email": "jane@example.com"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "handle": "fc3e6a9f-33d3-41bb-be0c-cb767f4019ea",
    "assertionOptionsJson": "{\"publicKey\":{\"challenge\":\"c1d2e3...\",\"rpId\":\"localhost\",\"allowCredentials\":[...]}}"
  }
  ```

### POST /login/verify
Verifies the passkey signature, establishes a session, and issues tokens.
- **Request Payload**:
  ```json
  {
    "handle": "fc3e6a9f-33d3-41bb-be0c-cb767f4019ea",
    "credential": { ...browser credential assertion response... }
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqYW5lQGV4YW1wbGUuY29tIiwidXNlcklkIjoxLCJuYW1lIjoiSmFuZSBEb2UiLCJpYXQiOjE2ODk5MDAwMDAsImV4cCI6MTY4OTkwMDkwMH0.signature",
    "user": {
      "id": 1,
      "name": "Jane Doe",
      "email": "jane@example.com",
      "phone": "+15550199"
    }
  }
  ```
- **Response Headers**:
  `Set-Cookie: refresh_token=abc123xyz...; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000`

### POST /session/refresh
Rotates the session. Consumes the cookie and returns a fresh access token and new cookie.
- **Request Headers**: Requires cookie `refresh_token=...`
- **Response**: `200 OK` (payload matches `POST /login/verify` schema)
- **Response Headers**:
  `Set-Cookie: refresh_token=new_token_value...; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000`

### POST /logout
Invalidates the current session.
- **Request Headers**: Requires cookie `refresh_token=...`
- **Response**: `200 OK`
  ```json
  {
    "message": "Logged out successfully."
  }
  ```
- **Response Headers**:
  `Set-Cookie: refresh_token=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0`

---

## 5. Account Recovery Endpoints

### POST /recover/start
Requests a passwordless account recovery link.
- **Request Payload**:
  ```json
  {
    "email": "jane@example.com"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "message": "If the account exists and is active, a recovery link has been sent."
  }
  ```

### POST /recover/verify
Verifies the recovery link and the user's recovery code. Begins the registration of a new passkey.
- **Request Payload**:
  ```json
  {
    "email": "jane@example.com",
    "token": "recovery-email-token-uuid-here",
    "recoveryCode": "QT88E-AH7AM"
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "handle": "4d7e9f3b-c2e8-469b-8e10-38c201a09cb4",
    "creationOptionsJson": "{\"publicKey\":{\"rp\":{\"name\":\"SecureBank\",\"id\":\"localhost\"},\"user\":{...}}}"
  }
  ```

### POST /recover/passkey
Finalizes account recovery, registers the new passkey, revokes old passkeys/codes, and logs in the user.
- **Request Payload**:
  ```json
  {
    "handle": "4d7e9f3b-c2e8-469b-8e10-38c201a09cb4",
    "credential": { ...browser PublicKeyCredential attestation response... }
  }
  ```
- **Response**: `200 OK`
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1...",
    "recoveryCodes": [
      "NEWCODE-11111",
      "NEWCODE-22222",
      "..."
    ]
  }
  ```
- **Response Headers**:
  `Set-Cookie: refresh_token=new_recovery_token...; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000`

---

## 6. Secure Endpoints

### GET /me
Fetches the current authenticated user's profile card.
- **Request Headers**: `Authorization: Bearer <accessToken>`
- **Response**: `200 OK`
  ```json
  {
    "id": 1,
    "name": "Jane Doe",
    "email": "jane@example.com",
    "phone": "+15550199"
  }
  ```
- **Errors**: `401 Unauthorized` if JWT is invalid, expired, or missing.
