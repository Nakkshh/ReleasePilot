import { request } from './http.js'

export const liveApi = {
  async health() {
    const response = await request('/api/health', { timeoutMs: 5000 })
    return { status: 'UP', message: response?.message || 'Backend responded' }
  },
  listDrafts: () => request('/api/release/drafts'),
  getDraft: (id) => request(`/api/release/drafts/${encodeURIComponent(id)}`),
  knowledgeStatus: () => request('/api/knowledge/status'),
  searchKnowledge: (q, topK = 5, minScore = 0) => request('/api/knowledge/search', { query: { q, topK, minScore } }),
  ask: (message) => request('/api/agent/ask', { method: 'POST', body: { message } }),
  releaseCheck: () => request('/api/agent/release-check', { method: 'POST' }),
  releaseNotes: () => request('/api/agent/release-notes', { method: 'POST' }),
  login: (username, password) => request('/api/auth/login', { method: 'POST', body: { username, password } }),
  me: () => request('/api/auth/me'),
  logout: async () => undefined,
}
