export class ApiError extends Error {
  constructor(message, { status = 0, code = 'UNKNOWN', retryAfter = null, details = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryAfter = retryAfter
    this.details = details
  }
}

export function describeApiError(error) {
  if (!(error instanceof ApiError)) return error?.message || 'Something unexpected happened.'
  if (error.status === 401) return 'Your session is missing or expired. Please sign in again.'
  if (error.status === 403) return 'Your account does not have permission to do that.'
  if (error.status === 422) return error.message || 'The request could not pass validation or policy checks.'
  if (error.status === 429) return `Too many requests. Try again${error.retryAfter ? ` in ${error.retryAfter} seconds` : ' shortly'}.`
  if (error.status === 502) return 'A required backend service failed. Please try again later.'
  if (error.code === 'TIMEOUT') return 'The request timed out. For actions that change data, refresh state before retrying.'
  if (error.code === 'NETWORK') return 'Could not reach the backend. Check that it is running and the API URL is correct.'
  return error.message || 'The request failed.'
}
