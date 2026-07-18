/**
 * Base HTTP client for the SecureBank authentication API.
 *
 * All requests include credentials so the (future) HttpOnly refresh-token
 * cookie is sent automatically. The Phase 2 registration endpoints are public
 * and CSRF-exempt, so no CSRF token is attached yet; that will be added when
 * authenticated, cookie-based endpoints arrive.
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

interface BackendMessage {
  message?: string
}

/**
 * POST JSON to the API and parse the JSON response. Throws ApiError with the
 * backend's message on non-2xx responses.
 */
export async function postJson<TResponse>(
  path: string,
  body: unknown,
): Promise<TResponse> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Unable to reach the server. Please try again.')
  }

  let payload: unknown = null
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
      (payload as BackendMessage | null)?.message ??
      'Something went wrong. Please try again.'
    throw new ApiError(response.status, message)
  }

  return payload as TResponse
}
