import { useCallback, useEffect, useState } from 'react'
import { describeApiError } from '../api/errors.js'

function Loading({ label = 'Loading draft details…' }) {
  return (
    <div className="inline-state">
      <span className="spinner" />
      {label}
    </div>
  )
}

function ErrorState({ error, onRetry }) {
  return (
    <div className="state-card error-state">
      <span className="state-symbol">!</span>
      <div>
        <strong>Couldn’t load this draft</strong>
        <p>{describeApiError(error)}</p>
        <button className="button button-secondary" onClick={onRetry}>
          Try again
        </button>
      </div>
    </div>
  )
}

function StatusPill({ status }) {
  const value = String(status || 'UNKNOWN').toUpperCase()
  let kind = 'neutral'

  if (value === 'READY' || value === 'APPROVED' || value === 'PUBLISHED') {
    kind = 'success'
  } else if (
    value === 'NOT_READY' ||
    value === 'BLOCKED' ||
    value === 'REJECTED' ||
    value === 'FAILED'
  ) {
    kind = 'danger'
  } else if (value === 'PENDING' || value === 'OPEN') {
    kind = 'warning'
  }

  return (
    <span className={`status-pill ${kind}`}>
      <span className="status-dot" />
      {value}
    </span>
  )
}

function formatDate(value) {
  if (!value) return 'Not provided'

  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? 'Date unavailable'
    : date.toLocaleString()
}

function Metadata({ label, value }) {
  return (
    <div className="draft-detail-metadata">
      <span>{label}</span>
      <strong>{value ?? 'Not provided'}</strong>
    </div>
  )
}

function EvidenceList({ title, items, emptyMessage }) {
  if (!Array.isArray(items) || items.length === 0) {
    return (
      <section className="draft-detail-section">
        <h3>{title}</h3>
        <p className="draft-detail-muted">{emptyMessage}</p>
      </section>
    )
  }

  return (
    <section className="draft-detail-section">
      <h3>{title}</h3>
      <ul className="draft-detail-list">
        {items.map((item, index) => (
          <li key={`${title}-${index}`}>{String(item)}</li>
        ))}
      </ul>
    </section>
  )
}

function NotesSection({ notes }) {
  if (!notes) {
    return (
      <section className="draft-detail-section">
        <h3>Release notes</h3>
        <p className="draft-detail-muted">
          No release notes were included in this draft response.
        </p>
      </section>
    )
  }

  const groups = [
    ['Features', notes.features],
    ['Fixes', notes.fixes],
    ['Maintenance', notes.chores],
    ['Breaking changes', notes.breakingChanges],
  ]

  return (
    <section className="draft-detail-section">
      <h3>{notes.title || 'Release notes'}</h3>

      {notes.summary && <p>{notes.summary}</p>}

      {groups.map(([label, items]) => (
        <EvidenceList
          key={label}
          title={label}
          items={items}
          emptyMessage={`No ${label.toLowerCase()} were provided.`}
        />
      ))}
    </section>
  )
}

export default function DraftDetail({ draftId, api, onBack }) {
  const [draft, setDraft] = useState(null)
  const [state, setState] = useState('loading')
  const [error, setError] = useState(null)

  const loadDraft = useCallback(async () => {
    if (draftId == null) {
      setError(new Error('No draft ID was selected.'))
      setState('error')
      return
    }

    setState('loading')
    setError(null)
    setDraft(null)

    try {
      const response = await api.getDraft(draftId)

      if (!response || typeof response !== 'object') {
        throw new Error('The API returned an invalid draft response.')
      }

      setDraft(response)
      setState('ready')
    } catch (requestError) {
      setError(requestError)
      setState('error')
    }
  }, [api, draftId])

  useEffect(() => {
    let cancelled = false

    Promise.resolve().then(() => {
      if (!cancelled) {
        loadDraft()
      }
    })

    return () => {
      cancelled = true
    }
  }, [loadDraft])

  const verdict = draft?.verdict
  const notes = draft?.notes

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">RELEASE OPERATIONS</p>
          <h1>Draft details</h1>
          <p className="page-description">
            Review release metadata and available readiness evidence.
          </p>
        </div>

        <div className="heading-actions">
          <button className="button button-secondary" onClick={onBack}>
            ← All drafts
          </button>

          <button
            className="button button-secondary"
            onClick={loadDraft}
            disabled={state === 'loading'}
          >
            ↻ Refresh
          </button>
        </div>
      </div>

      {state === 'loading' && <Loading />}

      {state === 'error' && (
        <div className="panel">
          <ErrorState error={error} onRetry={loadDraft} />
        </div>
      )}

      {state === 'ready' && draft && (
        <>
          <section className="panel draft-detail-summary">
            <div className="draft-detail-title-row">
              <div>
                <h2>{draft.title || draft.tag || `Draft #${draft.id}`}</h2>
                <p className="page-description">
                  Draft ID: {draft.id ?? 'Not provided'}
                </p>
              </div>

              <StatusPill status={draft.status} />
            </div>

            <div className="draft-detail-tags">
              <span className="score-chip">
                Tag: {draft.tag || 'Not provided'}
              </span>

              <span className="score-chip">
                Readiness: {draft.verdictStatus || verdict?.status || 'UNKNOWN'}
              </span>

              <span className="score-chip">
                Approval gate: {draft.approvalGate || 'Not provided'}
              </span>

              {draft.prerelease && (
                <span className="score-chip">Prerelease</span>
              )}
            </div>
          </section>

          <div className="draft-detail-grid">
            <div className="panel">
              <h2 className="draft-detail-panel-title">Readiness evidence</h2>

              {verdict ? (
                <>
                  <div className="draft-detail-verdict">
                    <StatusPill status={verdict.status} />
                    {verdict.summary && <p>{verdict.summary}</p>}
                  </div>

                  <EvidenceList
                    title="Reasons"
                    items={verdict.reasons}
                    emptyMessage="No reasons were returned."
                  />

                  <EvidenceList
                    title="Blockers"
                    items={verdict.blockers}
                    emptyMessage="No blockers were returned in this response."
                  />
                </>
              ) : (
                <p className="draft-detail-muted">
                  No detailed verdict was included in this response. The
                  readiness status shown above is the status supplied by the
                  draft endpoint; it does not imply a new check was run.
                </p>
              )}

              <NotesSection notes={notes} />

              <EvidenceList
                title="Warnings"
                items={draft.warnings}
                emptyMessage="No warnings were provided."
              />

              <EvidenceList
                title="Invalid references"
                items={draft.invalidReferences}
                emptyMessage="No invalid references were reported."
              />

              <EvidenceList
                title="Omitted pull requests"
                items={draft.omittedPrs}
                emptyMessage="No omitted pull requests were reported."
              />
            </div>

            <aside className="panel">
              <h2 className="draft-detail-panel-title">Release metadata</h2>

              <Metadata label="Version tag" value={draft.tag} />
              <Metadata label="Baseline" value={draft.baseline} />
              <Metadata label="Target SHA" value={draft.targetSha} />
              <Metadata label="Created by" value={draft.createdBy} />
              <Metadata label="Created at" value={formatDate(draft.createdAt)} />
              <Metadata label="Expires at" value={formatDate(draft.expiresAt)} />
              <Metadata label="Decided at" value={formatDate(draft.decidedAt)} />
              <Metadata label="Decision by" value={draft.decidedBy} />
              <Metadata label="Decision comment" value={draft.decisionComment} />
              <Metadata label="Check mode" value={draft.checkMode} />
              <Metadata label="Pull request count" value={draft.prCount} />
              <Metadata
                label="Override used"
                value={
                  typeof draft.overrideUsed === 'boolean'
                    ? draft.overrideUsed ? 'Yes' : 'No'
                    : 'Not provided'
                }
              />
              <Metadata label="Override reason" value={draft.overrideReason} />
              <Metadata label="Released at" value={formatDate(draft.releasedAt)} />
              <Metadata label="GitHub release ID" value={draft.githubReleaseId} />

              {draft.githubReleaseUrl && (
                <div className="draft-detail-metadata">
                  <span>GitHub release</span>
                  <a
                    href={draft.githubReleaseUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Open release ↗
                  </a>
                </div>
              )}

              {draft.hasError && (
                <p className="draft-detail-warning">
                  The API reports an error for this draft. Its details are not
                  exposed in this response.
                </p>
              )}

              {draft.lastError && (
                <p className="draft-detail-warning">
                  Latest error: {draft.lastError}
                </p>
              )}

              <p className="panel-footnote">
                Read-only view. No approval, rejection, retry or publishing
                request is made from this screen.
              </p>
            </aside>
          </div>
        </>
      )}
    </>
  )
}
