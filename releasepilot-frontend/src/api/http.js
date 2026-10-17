import { config } from '../config.js'
import { ApiError } from './errors.js'

let accessToken = null
export const setAccessToken = (token) => { accessToken = token || null }
export const clearAccessToken = () => { accessToken = null }
export const getAccessToken = () => accessToken

export async function request(path, { method = 'GET', body, query, timeoutMs = config.requestTimeoutMs } = {}) {
  const url = new URL(`${config.apiBaseUrl}${path}`, window.location.origin)
  if (query) Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  })
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs)
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
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
      try { data = JSON.parse(text) } catch { data = { message: text } }
    }
    if (!response.ok) {
      const message = data?.message || data?.detail || data?.error || `Request failed (${response.status}).`
      throw new ApiError(message, {
        status: response.status,
        code: response.status === 429 ? 'RATE_LIMITED' : 'HTTP_ERROR',
        retryAfter: retryAfter ? Number(retryAfter) : null,
        details: data,
      })
    }
    return data
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error.name === 'AbortError') throw new ApiError('Request timed out.', { code: 'TIMEOUT' })
    throw new ApiError('Could not reach the backend.', { code: 'NETWORK' })
  } finally {
    window.clearTimeout(timeout)
  }
}
