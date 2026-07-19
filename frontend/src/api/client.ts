/**
 * Base HTTP client for the SecureBank authentication API.
 */

export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/** Error carrying the backend's HTTP status and message. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let accessToken: string | null = null
let sessionExpiredHandler: (() => void) | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

export function setSessionExpiredHandler(handler: () => void): void {
  sessionExpiredHandler = handler
}

function getCsrfToken(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

let isRefreshing = false
let refreshQueue: ((token: string) => void)[] = []

async function performRefresh(): Promise<string> {
  if (isRefreshing) {
    return new Promise<string>((resolve) => {
      refreshQueue.push(resolve)
    })
  }

  isRefreshing = true
  try {
    const csrfToken = getCsrfToken()
    const response = await fetch(`${API_BASE_URL}/session/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
      },
    })
    if (!response.ok) {
      throw new ApiError(response.status, 'Session expired. Please log in again.')
    }
    const data = await response.json() as { accessToken: string }
    setAccessToken(data.accessToken)
    
    const token = data.accessToken
    refreshQueue.forEach((resolve) => resolve(token))
    refreshQueue = []
    
    return token
  } finally {
    isRefreshing = false
  }
}

/**
 * Attempt to restore an access token from the HttpOnly refresh cookie (e.g.
 * after a full page reload, where the in-memory access token is gone).
 * Returns true if a session was restored. Never throws.
 */
export async function tryRestoreSession(): Promise<boolean> {
  try {
    await performRefresh()
    return true
  } catch {
    return false
  }
}

export async function request<TResponse>(
  path: string,
  options: RequestInit = {}
): Promise<TResponse> {
  const headers = new Headers(options.headers)
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  const csrfToken = getCsrfToken()
  if (csrfToken && options.method && options.method !== 'GET') {
    headers.set('X-XSRF-TOKEN', csrfToken)
  }

  const mergedOptions: RequestInit = {
    ...options,
    credentials: 'include',
    headers,
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, mergedOptions)
  } catch {
    throw new ApiError(0, 'Unable to reach the server. Please try again.')
  }

  if (response.status === 401 && path !== '/session/refresh') {
    try {
      const newAccessToken = await performRefresh()
      // Retry with new token
      const retryHeaders = new Headers(mergedOptions.headers)
      retryHeaders.set('Authorization', `Bearer ${newAccessToken}`)
      const retryCsrf = getCsrfToken()
      if (retryCsrf && options.method && options.method !== 'GET') {
        retryHeaders.set('X-XSRF-TOKEN', retryCsrf)
      }
      response = await fetch(`${API_BASE_URL}${path}`, {
        ...mergedOptions,
        headers: retryHeaders,
      })
    } catch (refreshErr) {
      sessionExpiredHandler?.()
      throw refreshErr
    }
  }

  let payload: any = null
  const text = await response.text()
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = null
    }
  }

  if (!response.ok) {
    const message =
      payload?.message ?? 'Something went wrong. Please try again.'
    throw new ApiError(response.status, message)
  }

  return payload as TResponse
}

export function getJson<TResponse>(path: string): Promise<TResponse> {
  return request<TResponse>(path, {
    method: 'GET',
  })
}

export function postJson<TResponse>(
  path: string,
  body?: unknown
): Promise<TResponse> {
  return request<TResponse>(path, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
}
