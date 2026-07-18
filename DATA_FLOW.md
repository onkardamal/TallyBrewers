# Data Flow

Registration

User

↓

Frontend

↓

Backend

↓

Email Verification

↓

Create Passkey

↓

Store Public Key

↓

Generate Recovery Codes

↓

Dashboard

--------------------------------

Registration + Passkey (Phase 2, implemented — detailed)

POST /register
  → create user (status PENDING_VERIFICATION)
  → generate verification token, store SHA-256 hash, email raw token

POST /verify-email  (or POST /verify-email/resend to get a fresh token)
  → hash token, match, check not expired / not used
  → mark token used, user status → VERIFIED

POST /passkey/register/start   (user must be VERIFIED)
  → WebAuthn RelyingParty.startRegistration()
  → store PublicKeyCredentialCreationOptions in ChallengeStore (server-side)
  → return { handle, creationOptions } to browser

Browser navigator.credentials.create()  → attestation response

POST /passkey/register
  → consume challenge by handle
  → RelyingParty.finishRegistration() verifies attestation (real WebAuthn)
  → store passkey public key
  → first passkey only: generate 10 recovery codes (BCrypt-hashed),
    return plaintext once, user status → ACTIVE

--------------------------------

Login

User enters Email

↓

Generate Challenge

↓

Browser Requests Biometric

↓

User Authenticates

↓

Challenge Signed

↓

Verify Signature

↓

Create Session

↓

Dashboard

--------------------------------

Recovery

Email

↓

Recovery Code

↓

Register New Passkey

↓

Delete Old Passkey