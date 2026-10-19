
import { request, clearAccessToken } from './http.js'

export const liveApi = {
  async health() {
    const response = await request('/api/health', {
      timeoutMs: 5000,
    })

    return {
      status: 'UP',
      message: response?.message || 'Backend responded',
    }
  },

  listDrafts: () => request('/api/release/drafts'),

  getDraft: (id) =>
    request(`/api/release/drafts/${encodeURIComponent(id)}`),

  createDraft: (tag, prerelease = false) =>
    request('/api/release/drafts', {
      method: 'POST',
      body: { tag, prerelease },
    }),

  approveDraft: (id, {
    comment = '',
    override = false,
    overrideReason = '',
    title = '',
  } = {}) =>
    request(`/api/release/drafts/${encodeURIComponent(id)}/approve`, {
      method: 'POST',
      body: { comment, override, overrideReason, title },
    }),

  rejectDraft: (id, comment = '') =>
    request(`/api/release/drafts/${encodeURIComponent(id)}/reject`, {
      method: 'POST',
      body: { comment },
    }),

  retryDraft: (id) =>
    request(`/api/release/drafts/${encodeURIComponent(id)}/retry`, {
      method: 'POST',
    }),

  knowledgeStatus: () => request('/api/knowledge/status'),

  searchKnowledge: (q, topK = 5, minScore = 0) =>
    request('/api/knowledge/search', {
      query: { q, topK, minScore },
    }),

  ask: (message) =>
    request('/api/agent/ask', {
      method: 'POST',
      body: { message },
    }),

  releaseCheck: () =>
    request('/api/agent/release-check', {
      method: 'POST',
    }),

  releaseNotes: () =>
    request('/api/agent/release-notes', {
      method: 'POST',
    }),

  login: (username, password) =>
    request('/api/auth/login', {
      method: 'POST',
      body: { username, password },
    }),

  me: () => request('/api/auth/me'),

  changePassword: (currentPassword, newPassword) =>
    request('/api/auth/password', {
      method: 'POST',
      body: { currentPassword, newPassword },
    }),

  async logout() {
    clearAccessToken()
  },
}
