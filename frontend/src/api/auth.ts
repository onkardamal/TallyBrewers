/**
 * Typed API functions for the SecureBank registration + passkey flow.
 *
 * These map 1:1 to the implemented backend endpoints (see API.md):
 *   POST /register
 *   POST /verify-email
 *   POST /verify-email/resend
 *   POST /passkey/register/start
 *   POST /passkey/register
 */
import { postJson } from './client'

export interface MessageResponse {
  message: string
}

export interface RegisterRequest {
  name: string
  email: string
  phone?: string
}

export function register(request: RegisterRequest): Promise<MessageResponse> {
  return postJson<MessageResponse>('/register', request)
}

export function verifyEmail(token: string): Promise<MessageResponse> {
  return postJson<MessageResponse>('/verify-email', { token })
}

export function resendVerification(email: string): Promise<MessageResponse> {
  return postJson<MessageResponse>('/verify-email/resend', { email })
}

/**
 * Response from /passkey/register/start. `creationOptions` is the raw
 * PublicKeyCredentialCreationOptions JSON ({ publicKey: {...} }) to feed to
 * the browser WebAuthn API.
 */
export interface PasskeyStartResponse {
  handle: string
  creationOptions: { publicKey: unknown }
}

export function passkeyRegisterStart(
  email: string,
): Promise<PasskeyStartResponse> {
  return postJson<PasskeyStartResponse>('/passkey/register/start', { email })
}

export interface PasskeyFinishResponse {
  recoveryCodes: string[]
}

export function passkeyRegisterFinish(
  handle: string,
  credential: unknown,
): Promise<PasskeyFinishResponse> {
  return postJson<PasskeyFinishResponse>('/passkey/register', {
    handle,
    credential,
  })
}
