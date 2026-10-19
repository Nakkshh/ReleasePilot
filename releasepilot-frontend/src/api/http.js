
import { config } from '../config.js'
import { ApiError } from './errors.js'

let accessToken = null
let tokenExpiresAt = null

function notifySessionExpired() {
  accessToken = null
  tokenExpiresAt = null

  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('releasepilot:session-expired'))
  }
}

export function setAccessToken(token, expiresAt = null) {
  accessToken = token || null
  tokenExpiresAt = expiresAt ? new Date(expiresAt).getTime() : null
}

export function clearAccessToken() {
  accessToken = null
  tokenExpiresAt = null
}

export function getAccessToken() {
  return accessToken
}

export function getTokenExpiresAt() {
  return tokenExpiresAt
}

export function isSessionExpired() {
  return Boolean(
    accessToken &&
    tokenExpiresAt &&
    Date.now() >= tokenExpiresAt
  )
}

export async function request(
  path,
  {
    method = 'GET',
    body,
    query,
    timeoutMs = config.requestTimeoutMs,
  } = {},
) {
  if (isSessionExpired()) {
    notifySessionExpired()
    throw new ApiError('Your session has expired. Please sign in again.', {
      status: 401,
      code: 'SESSION_EXPIRED',
    })
  }

  const url = new URL(
    `${config.apiBaseUrl}${path}`,
    window.location.origin,
  )

  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value))
      }
    })
  }

  const controller = new AbortController()
  const timeout = window.setTimeout(
    () => controller.abort(),
    timeoutMs,
  )

  const headers = { Accept: 'application/json' }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })

    const retryAfter = response.headers.get('Retry-After')
    const text = response.status === 204 ? '' : await response.text()

    let data = null

    if (text) {
      try {
        data = JSON.parse(text)
      } catch {
        data = { message: text }
      }
    }

    if (!response.ok) {
      const message =
        data?.message ||
        data?.detail ||
        data?.error ||
        `Request failed (${response.status}).`

      if (response.status === 401 && path !== '/api/auth/login') {
        notifySessionExpired()
      }

      throw new ApiError(message, {
        status: response.status,
        code: response.status === 429
          ? 'RATE_LIMITED'
          : response.status === 401
            ? 'UNAUTHORIZED'
            : 'HTTP_ERROR',
        retryAfter: retryAfter ? Number(retryAfter) : null,
        details: data,
      })
    }

    return data
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }

    if (error.name === 'AbortError') {
      throw new ApiError('Request timed out.', {
        code: 'TIMEOUT',
      })
    }

    throw new ApiError('Could not reach the backend.', {
      code: 'NETWORK',
    })
  } finally {
    window.clearTimeout(timeout)
  }
}
