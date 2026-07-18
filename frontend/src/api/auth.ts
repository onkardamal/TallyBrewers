/**
 * Typed API functions for the SecureBank authentication service.
 */
import { postJson, getJson } from './client'

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

export function loginVerify(
  handle: string,
  credential: unknown,
): Promise<LoginResponse> {
  return postJson<LoginResponse>('/login/verify', { handle, credential })
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
