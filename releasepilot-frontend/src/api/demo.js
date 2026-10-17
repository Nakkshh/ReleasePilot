const demoDrafts = [
  { id: 24, tag: 'v2.4.0', title: 'Release v2.4.0', status: 'PENDING', approvalGate: 'OPEN', verdictStatus: 'READY', prerelease: false, createdAt: '2026-10-17T10:15:00Z', createdBy: 'demo-user' },
  { id: 23, tag: 'v2.3.2', title: 'Release v2.3.2', status: 'PENDING', approvalGate: 'OPEN', verdictStatus: 'UNKNOWN', prerelease: false, createdAt: '2026-10-17T09:30:00Z', createdBy: 'demo-user' },
  { id: 25, tag: 'v2.5.0-beta.1', title: 'Release v2.5.0-beta.1', status: 'BLOCKED', approvalGate: 'CLOSED', verdictStatus: 'NOT_READY', prerelease: true, createdAt: '2026-10-16T14:20:00Z', createdBy: 'demo-user' },
]

export const demoApi = {
  async health() { return { status: 'DEMO', message: 'Demo adapter active' } },
  async listDrafts() { return structuredClone(demoDrafts) },
  async getDraft(id) { return structuredClone(demoDrafts.find((draft) => String(draft.id) === String(id)) || demoDrafts[0]) },
  async knowledgeStatus() { return { available: true, mode: 'demo', note: 'Recorded example status; not a live index check.' } },
  async searchKnowledge(q) {
    return { query: q, topK: 2, minScore: 0, elapsedMs: 8, hits: [
      { source: 'runbooks/rollback-runbook.md', heading: 'Rollback procedure', score: 0.91, text: 'Review the last known good version, validate service health and follow the approved rollback procedure.' },
      { source: 'knowledge/release-policy.md', heading: 'Release readiness', score: 0.83, text: 'Verify test evidence, release notes and the required approval before publishing.' },
    ] }
  },
  async ask(message) { return { reply: `Demo response for: “${message}”. In Live mode, this will call the supported /api/agent/ask endpoint. This sample is not model-generated.`, timestamp: new Date().toISOString() } },
  async releaseCheck() { return { verdict: 'UNKNOWN', mode: 'demo', note: 'No live readiness check was run.', trace: [], modelCalls: 0, toolRounds: 0, elapsedMs: 0, promptTokens: null, completionTokens: null, timestamp: new Date().toISOString() } },
  async releaseNotes() { return { notes: { title: 'Sample release notes', summary: 'No live generation was requested.', features: [], fixes: [], chores: [], breakingChanges: [] }, baseline: 'Demo fixture', prCount: 0, invalidReferences: [], omittedPrs: [], modelCalls: 0, elapsedMs: 0, promptTokens: null, completionTokens: null, timestamp: new Date().toISOString() } },
  async login() { throw new Error('Sign-in is disabled in Demo mode. Switch to Live mode to use the backend login endpoint.') },
  async me() { return null },
  async logout() { return undefined },
}
