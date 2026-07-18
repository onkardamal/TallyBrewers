# UI Rules

Minimal.

Modern.

Professional.

Inspired by

Apple

Stripe

Google

No Dashboard First.

First screen is Login.

Maximum 2 actions per screen.

No unnecessary animations.

Accessibility.

Responsive.

Fast.

Clean.


---

## Implemented screens (registration flow)

All screens use a shared centered `AuthLayout` (SecureBank wordmark, title,
optional subtitle, single card), built with TailwindCSS utilities only. Each
screen keeps to at most two primary actions.

Route map (react-router-dom):

- `/`               Login (first screen). Passkey sign-in arrives in Phase 3;
                    currently links to registration.
- `/register`       Register — name, email, optional phone → `POST /register`.
- `/verify-email`   Two modes:
                    - with `?token=` (from the email link): auto-calls
                      `POST /verify-email`, then offers "Set up passkey".
                    - without a token: "check your inbox" with a resend action
                      (`POST /verify-email/resend`).
- `/passkey-setup`  Runs the real WebAuthn ceremony: `POST /passkey/register/start`
                    → browser `navigator.credentials.create()` → `POST /passkey/register`.
- `/recovery-codes` One-time display of the returned recovery codes; requires
                    an explicit "I have saved them" confirmation to continue.
- `/dashboard`      Minimal authenticated landing placeholder (not a banking
                    dashboard).
- `/recover`        Placeholder (recovery flow is a later phase).

WebAuthn is performed with the browser's native
`PublicKeyCredential.parseCreationOptionsFromJSON()` / `.toJSON()` helpers,
which produce/consume exactly the base64url JSON format the server-side Yubico
library expects. This is real WebAuthn — the platform authenticator (Touch ID
/ Windows Hello / security key) is invoked; nothing is simulated in the app.

### Screenshots

Static screenshots are not committed to the repository. Capturing them
requires running the app in a real browser (and, for the passkey screen, a
real platform authenticator prompt), which cannot be produced headlessly in
this environment. To capture them manually:

1. Start the backend: `cd auth-service` then run the app (see README/run steps).
2. Start the frontend: `cd frontend` then `npm run dev` (http://localhost:5173).
3. Walk through `/register` → email link → `/passkey-setup` → `/recovery-codes`
   and capture each screen.

Place any captured images under `docs/screenshots/` and link them here.
