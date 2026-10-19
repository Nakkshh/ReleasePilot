import draftFixtures from './fixtures/drafts.json'
import draftDetailFixtures from './fixtures/draft-details.json'
import knowledgeStatusFixture from './fixtures/knowledge-status.json'
import knowledgeSearchFixture from './fixtures/knowledge-search.json'
import askFixture from './fixtures/ask.json'
import releaseCheckFixture from './fixtures/release-check.json'
import releaseNotesFixture from './fixtures/release-notes.json'

function clone(value) {
  return structuredClone(value)
}

export const demoApi = {
  async health() {
    return {
      status: 'DEMO',
      message: 'Demo adapter active',
      note: 'This is not a live backend health check.',
    }
  },

  async listDrafts() {
    return clone(draftFixtures)
  },

  async getDraft(id) {
    const draft = draftDetailFixtures[String(id)]

    if (!draft) {
      throw new Error(`Draft ${id} is not present in the demo fixtures.`)
    }

    return clone(draft)
  },

  async knowledgeStatus() {
    return clone(knowledgeStatusFixture)
  },

  async searchKnowledge(query) {
    return {
      ...clone(knowledgeSearchFixture),
      query,
    }
  },

  async ask(message) {
    return {
      ...clone(askFixture),
      reply: `${askFixture.reply}\n\nYour demo question was: ${message}`,
    }
  },

  async releaseCheck() {
    return clone(releaseCheckFixture)
  },

  async releaseNotes() {
    return clone(releaseNotesFixture)
  },

  async login() {
    throw new Error(
      'Sign-in is disabled in Demo mode. Switch to Live mode to use the backend login endpoint.',
    )
  },

  async me() {
    return null
  },

  async logout() {
    return undefined
  },

  async createDraft() {
    throw new Error(
      'Creating drafts is disabled in Demo mode. No draft was created.',
    )
  },

  async approveDraft() {
    throw new Error(
      'Approval is disabled in Demo mode. No release was approved.',
    )
  },

  async rejectDraft() {
    throw new Error(
      'Rejection is disabled in Demo mode. No draft was rejected.',
    )
  },

  async retryDraft() {
    throw new Error(
      'Retry is disabled in Demo mode. No release action was performed.',
    )
  },

  async changePassword() {
    throw new Error(
      'Password changes require Live mode and an authenticated account.',
    )
  },
}