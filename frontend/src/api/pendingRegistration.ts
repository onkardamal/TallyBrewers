/**
 * Remembers the email being registered in the current browser so the
 * verification and passkey-setup screens can pre-fill it. This is a UX
 * convenience only; it is not a security boundary. If the verification link
 * is opened in a different browser, the user simply re-enters their email.
 */
const KEY = 'securebank.pendingEmail'

export function setPendingEmail(email: string): void {
  try {
    localStorage.setItem(KEY, email)
  } catch {
    // Ignore storage failures (e.g. private mode); email can be re-entered.
  }
}

export function getPendingEmail(): string {
  try {
    return localStorage.getItem(KEY) ?? ''
  } catch {
    return ''
  }
}

export function clearPendingEmail(): void {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // Ignore.
  }
}
