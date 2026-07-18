# APIs

## Endpoint list

POST /register

POST /verify-email

POST /verify-email/resend

POST /passkey/register/start

POST /passkey/register

POST /login/start

POST /login/verify

POST /session/refresh

POST /logout

POST /recover/start

POST /recover/verify

POST /recover/passkey

Notes:

- `/verify-email/resend` and `/passkey/register/start` were added during the
  Phase 2 architecture review. `/passkey/register/start` is the required
  companion "begin ceremony" endpoint for WebAuthn registration (the WebAuthn
  spec is a two-step start/finish ceremony). `/verify-email/resend` re-issues
  a verification email and invalidates the previous token.

---

## Phase 2 endpoints (implemented)

### POST /register
Create a new account and send a verification email.

Request:
```json
{ "name": "Alice Adams", "email": "alice@example.com", "phone": "+1..." }
```
- `phone` is optional.
- Response: `201 Created` with a generic message. To prevent account
  enumeration, the same response is returned whether or not the email already
  exists. The verification token is delivered only via email and is never
  returned in the response body.

### POST /verify-email
Verify an email address using the token from the verification link.

Request:
```json
{ "token": "<raw token from email link>" }
```
- The token is SHA-256 hashed and matched against stored hashes.
- Single-use: a token that is unknown, expired (24h TTL), or already used is
  rejected with `400 Bad Request` and a generic message.
- On success (`200 OK`) the user's status advances to `VERIFIED`.

### POST /verify-email/resend
Re-issue a verification email.

Request:
```json
{ "email": "alice@example.com" }
```
- Rate-limited (one request per 60s per email) → `429 Too Many Requests`.
- Invalidates any previously issued, still-unverified tokens for the user, so
  old links stop working.
- Always returns a generic `200 OK` response regardless of whether the account
  exists or is already verified (enumeration protection).

### POST /passkey/register/start
Begin a WebAuthn passkey registration ceremony. Requires a `VERIFIED` user.

Request:
```json
{ "email": "alice@example.com" }
```
- Keyed by email in this phase because login/sessions do not exist yet. Once
  sessions are introduced, this will be derived from the authenticated
  principal instead.
- Response `200 OK`:
```json
{
  "handle": "<opaque server-side ceremony handle>",
  "creationOptions": { "publicKey": { ... } }
}
```
- `creationOptions` is passed directly to the browser's
  `navigator.credentials.create()`.
- The challenge is stored server-side (behind a ChallengeStore abstraction)
  and is never trusted from the client on finish. Ceremonies expire after 5
  minutes and are single-use.
- `400 Bad Request` if the email is not yet verified; `404 Not Found` if no
  such account.

### POST /passkey/register
Finish the ceremony: verify the authenticator's attestation and store the
passkey.

Request:
```json
{
  "handle": "<handle from start>",
  "credential": { ...browser PublicKeyCredential attestation response... }
}
```
- The Yubico WebAuthn library performs full, real attestation verification.
  There is no mock/bypass path.
- Response `200 OK`:
```json
{ "recoveryCodes": ["ABCDE-FGHJK", "..."] }
```
- `recoveryCodes` (10 codes) is populated ONLY on the user's first passkey
  registration and is shown exactly once — the plaintext is never retrievable
  again (only BCrypt hashes are stored). On subsequent passkey registrations
  the array is empty.
- On first passkey the account status advances to `ACTIVE`.
- `400 Bad Request` if the ceremony expired or attestation verification fails.
