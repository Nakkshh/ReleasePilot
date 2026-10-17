const env = import.meta.env

export const config = {
  apiBaseUrl: (env.VITE_API_BASE_URL || '').replace(/\/$/, ''),
  initialMode: env.VITE_DEFAULT_MODE === 'live' ? 'live' : 'demo',
  requestTimeoutMs: Number(env.VITE_API_TIMEOUT_MS || 15000),
}
