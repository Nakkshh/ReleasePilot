
import { useEffect, useState } from 'react'
import { getAccessToken } from '../api/http.js'
import { describeApiError } from '../api/errors.js'

function ActionError({ error }) {
  if (!error) return null

  return (
    <div className="draft-action-error" role="alert">
      {describeApiError(error)}
    </div>
  )
}

function ActionResult({ message }) {
  if (!message) return null

  return (
    <div className="draft-action-success" role="status">
      {message}
    </div>
  )
}

export default function DraftManagement({
  api,
  mode,
  drafts = [],
  onChanged,
}) {
  const [username, setUsername] = useState('')
  const [roles, setRoles] = useState([])
  const [tag, setTag] = useState('')
  const [prerelease, setPrerelease] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState(null)
  const [createMessage, setCreateMessage] = useState('')
  const [actionError, setActionError] = useState(null)
  const [actionMessage, setActionMessage] = useState('')
  const [comments, setComments] = useState({})
  const [overrides, setOverrides] = useState({})
  const [overrideReasons, setOverrideReasons] = useState({})
  const [releaseTitles, setReleaseTitles] = useState({})

  const signedIn = Boolean(getAccessToken())
  const canApprove = roles.includes('APPROVER') || roles.includes('ADMIN')

  useEffect(() => {
    let active = true

    async function loadAccount() {
      if (!getAccessToken() || mode !== 'live') {
        setUsername('')
        setRoles([])
        return
      }

      try {
        const account = await api.me()
        if (!active) return

        setUsername(account?.username || '')
        setRoles(Array.isArray(account?.roles) ? account.roles : [])
      } catch {
        if (!active) return

        setUsername('')
        setRoles([])
      }
    }

    loadAccount()

    return () => {
      active = false
    }
  }, [api, mode])

  async function createDraft(event) {
    event.preventDefault()
    if (creating || mode !== 'live') return

    setCreating(true)
    setCreateError(null)
    setCreateMessage('')

    try {
      const result = await api.createDraft(tag.trim(), prerelease)

      setTag('')
      setPrerelease(false)
      setCreateMessage(
        `Draft created${result?.id != null ? ` (#${result.id})` : ''}.`,
      )

      await onChanged?.()
    } catch (error) {
      setCreateError(error)
    } finally {
      setCreating(false)
    }
  }

  async function runAction(draft, action) {
    if (busyId !== null || mode !== 'live') return

    setBusyId(draft.id)
    setActionError(null)
    setActionMessage('')

    const comment = comments[draft.id] || ''
    const override = Boolean(overrides[draft.id])
    const overrideReason = (overrideReasons[draft.id] || '').trim()
    const title = (releaseTitles[draft.id] || '').trim()

    try {
      let result

      if (action === 'approve') {
        if (draft.approvalGate === 'BLOCKED_INVALID_NOTES') {
          throw new Error(
            'This draft has invalid release notes. Fix the notes; an override cannot bypass this gate.',
          )
        }

        const needsOverride =
          draft.approvalGate === 'NEEDS_OVERRIDE' ||
          (draft.verdictStatus && draft.verdictStatus !== 'READY')

        if (needsOverride && !override) {
          throw new Error(
            'This draft is not READY. Check the override box and provide a reason.',
          )
        }

        if (needsOverride && overrideReason.length < 10) {
          throw new Error(
            'The override reason must contain at least 10 characters.',
          )
        }

        result = await api.approveDraft(draft.id, {
          comment,
          override,
          overrideReason,
          title,
        })
      } else if (action === 'reject') {
        result = await api.rejectDraft(draft.id, comment)
      } else if (action === 'retry') {
        result = await api.retryDraft(draft.id)
      }

      setActionMessage(
        `${action[0].toUpperCase()}${action.slice(1)} request completed for ${draft.tag || `draft #${draft.id}`}.`,
      )

      await onChanged?.()

      if (result?.status === 'RELEASED') {
        setActionMessage(
          `Backend reports ${draft.tag || `draft #${draft.id}`} as RELEASED.`,
        )
      }
    } catch (error) {
      setActionError(error)
    } finally {
      setBusyId(null)
    }
  }

  if (mode !== 'live') {
    return (
      <section className="panel draft-management">
        <div className="panel-heading">
          <div>
            <h2>Draft actions</h2>
            <p>Writes are disabled in Demo mode.</p>
          </div>
          <span className="status-pill neutral">
            <span className="status-dot" />
            Read only
          </span>
        </div>
        <p className="form-note">
          Switch to Live mode and sign in with an APPROVER or ADMIN account
          to create, approve, reject, or retry drafts. No live action was sent.
        </p>
      </section>
    )
  }

  if (!signedIn) {
    return (
      <section className="panel draft-management">
        <div className="panel-heading">
          <div>
            <h2>Draft actions</h2>
            <p>Authentication is required for release writes.</p>
          </div>
        </div>
        <p className="form-note">
          Sign in first. Anonymous visitors can inspect drafts but cannot
          create or change them.
        </p>
      </section>
    )
  }

  if (!canApprove) {
    return (
      <section className="panel draft-management">
        <div className="panel-heading">
          <div>
            <h2>Draft actions</h2>
            <p>Release writes require the APPROVER role.</p>
          </div>
          <span className="status-pill neutral">
            <span className="status-dot" />
            No write access
          </span>
        </div>
        <p className="form-note">
          Signed in as {username || 'current user'}. Your backend roles do
          not include APPROVER or ADMIN. The server remains the authority
          for all permissions.
        </p>
      </section>
    )
  }

  return (
    <section className="panel draft-management">
      <div className="panel-heading">
        <div>
          <h2>Release workflow</h2>
          <p>
            Signed in as {username || 'current user'} · {roles.join(', ')}
          </p>
        </div>
        <span className="status-pill success">
          <span className="status-dot" />
          Write access
        </span>
      </div>

      <section className="draft-create-section">
        <h3>Create a draft</h3>
        <p className="form-note">
          Use a version tag such as v1.2.3 or v1.2.3-rc.1. The backend
          performs the actual repository and readiness checks.
        </p>

        <form className="draft-create-form" onSubmit={createDraft}>
          <label>
            Release tag
            <input
              value={tag}
              onChange={(event) => setTag(event.target.value)}
              placeholder="v1.2.3"
              pattern="v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?"
              title="Use a tag such as v1.2.3 or v1.2.3-rc.1"
              required
              maxLength={100}
            />
          </label>

          <label className="draft-checkbox-label">
            <input
              type="checkbox"
              checked={prerelease}
              onChange={(event) => setPrerelease(event.target.checked)}
            />
            Mark as prerelease
          </label>

          <button
            className="button button-primary"
            disabled={creating || !tag.trim()}
          >
            {creating ? 'Creating…' : 'Create draft'}
          </button>
        </form>

        <ActionError error={createError} />
        <ActionResult message={createMessage} />
      </section>

      <div className="draft-action-list">
        <h3>Manage existing drafts</h3>

        {drafts.length === 0 && (
          <p className="form-note">No drafts are currently loaded.</p>
        )}

        {drafts.map((draft) => {
          const isBusy = busyId === draft.id
          const isBlocked = draft.approvalGate === 'BLOCKED_INVALID_NOTES'
          const notReady =
            draft.approvalGate === 'NEEDS_OVERRIDE' ||
            (draft.verdictStatus && draft.verdictStatus !== 'READY')
          const canOverride = notReady && !isBlocked

          return (
            <article className="draft-action-card" key={draft.id}>
              <div className="draft-action-card-heading">
                <div>
                  <strong>{draft.title || draft.tag || `Draft #${draft.id}`}</strong>
                  <p>
                    ID {draft.id} · {draft.status || 'Unknown status'} ·
                    {' '}{draft.verdictStatus || 'Verdict unknown'}
                  </p>
                </div>
                <span className="status-pill neutral">
                  <span className="status-dot" />
                  {draft.approvalGate || 'Gate unknown'}
                </span>
              </div>

              <label>
                Comment
                <textarea
                  value={comments[draft.id] || ''}
                  onChange={(event) =>
                    setComments((previous) => ({
                      ...previous,
                      [draft.id]: event.target.value,
                    }))
                  }
                  maxLength={1000}
                  rows={2}
                  placeholder="Optional review comment"
                />
              </label>

              <label>
                Release title override (optional)
                <input
                  value={releaseTitles[draft.id] || ''}
                  onChange={(event) =>
                    setReleaseTitles((previous) => ({
                      ...previous,
                      [draft.id]: event.target.value,
                    }))
                  }
                  maxLength={300}
                  placeholder="Leave blank to use the generated title"
                />
              </label>

              {canOverride && (
                <div className="draft-override-fields">
                  <label className="draft-checkbox-label">
                    <input
                      type="checkbox"
                      checked={Boolean(overrides[draft.id])}
                      onChange={(event) =>
                        setOverrides((previous) => ({
                          ...previous,
                          [draft.id]: event.target.checked,
                        }))
                      }
                    />
                    I explicitly authorize overriding the non-READY verdict
                  </label>

                  <label>
                    Override reason (at least 10 characters)
                    <textarea
                      value={overrideReasons[draft.id] || ''}
                      onChange={(event) =>
                        setOverrideReasons((previous) => ({
                          ...previous,
                          [draft.id]: event.target.value,
                        }))
                      }
                      minLength={10}
                      maxLength={1000}
                      rows={2}
                      placeholder="Explain why you are proceeding despite the verdict"
                    />
                  </label>
                </div>
              )}

              {isBlocked && (
                <p className="draft-action-warning">
                  Invalid release notes block approval. An override is not
                  available for this gate.
                </p>
              )}

              <div className="draft-action-buttons">
                <button
                  className="button button-primary"
                  disabled={
                    isBusy ||
                    isBlocked ||
                    (notReady &&
                      (!overrides[draft.id] ||
                        (overrideReasons[draft.id] || '').trim().length < 10))
                  }
                  onClick={() => runAction(draft, 'approve')}
                >
                  {isBusy ? 'Working…' : 'Approve'}
                </button>

                <button
                  className="button button-secondary"
                  disabled={isBusy}
                  onClick={() => runAction(draft, 'reject')}
                >
                  Reject
                </button>

                <button
                  className="button button-secondary"
                  disabled={isBusy}
                  onClick={() => runAction(draft, 'retry')}
                >
                  Retry release
                </button>
              </div>
            </article>
          )
        })}
      </div>

      <ActionError error={actionError} />
      <ActionResult message={actionMessage} />

      <p className="panel-footnote">
        These buttons call the authenticated backend. Approval may publish
        a GitHub release; retry may create or adopt one. Check the selected
        draft carefully before proceeding.
      </p>
    </section>
  )
}
