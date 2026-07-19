/**
 * Typed API functions for the SecureBank authentication service.
 */
import { postJson, getJson, request } from './client'

export interface MessageResponse {
  message: string
}

export interface RegisterRequest {
  name: string
  email: string
  phone?: string
}

export interface UserDto {
  id: number
  name: string
  email: string
  phone?: string
}

export interface LoginResponse {
  accessToken: string
  user: UserDto
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

export interface LoginStartResponse {
  handle: string
  assertionOptionsJson: string
}

export function loginStart(email: string): Promise<LoginStartResponse> {
  return postJson<LoginStartResponse>('/login/start', { email })
}

export interface LoginVerifyResponse {
  stepUpRequired: boolean
  stepUpHandle: string | null
  accessToken: string | null
  user: UserDto | null
}

export function loginVerify(
  handle: string,
  credential: unknown,
): Promise<LoginVerifyResponse> {
  return postJson<LoginVerifyResponse>('/login/verify', { handle, credential })
}

/** Complete a new-device step-up by submitting the emailed one-time code. */
export function loginStepUp(handle: string, code: string): Promise<LoginResponse> {
  return postJson<LoginResponse>('/login/step-up', { handle, code })
}

// ── Cross-device (QR "scan to sign in") login ──────────────────────────────

export interface QrStartResponse {
  linkId: string
  desktopToken: string
  expiresInSeconds: number
}

export interface QrStatusResponse {
  status: 'PENDING' | 'APPROVED' | 'EXPIRED' | 'CONSUMED'
  accessToken: string | null
  user: UserDto | null
}

/** Desktop: begin a QR login and receive the link id + secret device token. */
export function qrStart(): Promise<QrStartResponse> {
  return postJson<QrStartResponse>('/login/qr/start')
}

/** Desktop: poll for approval; on APPROVED the session (cookie + token) is issued. */
export function qrStatus(linkId: string, desktopToken: string): Promise<QrStatusResponse> {
  const params = new URLSearchParams({ linkId, desktopToken })
  return getJson<QrStatusResponse>(`/login/qr/status?${params.toString()}`)
}

/** Phone (authenticated): begin the passkey re-auth needed to approve. */
export function qrApproveStart(): Promise<LoginStartResponse> {
  return postJson<LoginStartResponse>('/login/qr/approve/start')
}

/** Phone (authenticated): approve a desktop sign-in with a fresh passkey signature. */
export function qrApprove(
  linkId: string,
  handle: string,
  credential: unknown,
): Promise<MessageResponse> {
  return postJson<MessageResponse>('/login/qr/approve', { linkId, handle, credential })
}

export function logout(): Promise<MessageResponse> {
  return postJson<MessageResponse>('/logout')
}

export function getMe(): Promise<UserDto> {
  return getJson<UserDto>('/me')
}

export function startRecovery(email: string): Promise<MessageResponse> {
  return postJson<MessageResponse>('/recover/start', { email })
}

export interface RecoveryVerifyResponse {
  handle: string
  creationOptionsJson: string
}

export function verifyRecovery(
  email: string,
  token: string,
  recoveryCode: string,
): Promise<RecoveryVerifyResponse> {
  return postJson<RecoveryVerifyResponse>('/recover/verify', {
    email,
    token,
    recoveryCode,
  })
}

export interface RecoveryCompleteResponse {
  accessToken: string
  recoveryCodes: string[]
}

export function completeRecovery(
  handle: string,
  credential: unknown,
): Promise<RecoveryCompleteResponse> {
  return postJson<RecoveryCompleteResponse>('/recover/passkey', {
    handle,
    credential,
  })
}

export interface SessionDto {
  id: number
  ipAddress?: string
  userAgent?: string
  createdAt: string
  lastActivity: string
  expiresAt: string
  current: boolean
}

/** List the signed-in user's active sessions (real, persisted session data). */
export function listSessions(): Promise<SessionDto[]> {
  return getJson<SessionDto[]>('/sessions')
}

/** Remotely sign a device out by revoking its session. */
export function revokeSession(id: number): Promise<MessageResponse> {
  return request<MessageResponse>(`/sessions/${id}`, { method: 'DELETE' })
}
