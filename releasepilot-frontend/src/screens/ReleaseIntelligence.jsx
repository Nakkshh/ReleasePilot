import { useState } from 'react'
import { describeApiError } from '../api/errors.js'

function Loading({ label }) {
  return (
    <div className="inline-state">
      <span className="spinner" />
      {label}
    </div>
  )
}

function ErrorPanel({ error, onRetry }) {
  return (
    <div className="state-card error-state" role="alert">
      <span className="state-symbol">!</span>
      <div>
        <strong>Request failed</strong>
        <p>{describeApiError(error)}</p>
        <button
          className="button button-secondary"
          onClick={onRetry}
          type="button"
        >
          Try again
        </button>
      </div>
    </div>
  )
}

function EmptyList({ children }) {
  return <p className="intelligence-muted">{children}</p>
}

function EvidenceList({ items, emptyMessage }) {
  if (!Array.isArray(items) || items.length === 0) {
    return <EmptyList>{emptyMessage}</EmptyList>
  }

  return (
    <ul className="intelligence-list">
      {items.map((item, index) => (
        <li key={`${index}-${item}`}>{item}</li>
      ))}
    </ul>
  )
}

function Metadata({ label, value }) {
  return (
    <div className="intelligence-metadata">
      <span>{label}</span>
      <strong>{value ?? 'Not provided'}</strong>
    </div>
  )
}

function ReadinessResults({ result }) {
  const verdict = result?.verdict
  const status = verdict?.status || 'UNKNOWN'

  const statusKind =
    status === 'READY'
      ? 'success'
      : status === 'NOT_READY'
        ? 'danger'
        : 'neutral'

  return (
    <div className="intelligence-results">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Readiness verdict</h2>
            <p>Decision and evidence returned by the backend.</p>
          </div>
          <span className={`status-pill ${statusKind}`}>
            <span className="status-dot" />
            {status}
          </span>
        </div>

        <p className="intelligence-summary">
          {verdict?.summary || 'No verdict summary was supplied.'}
        </p>

        <div className="intelligence-section">
          <h3>Reasons</h3>
          <EvidenceList
            items={verdict?.reasons}
            emptyMessage="The response contains no reasons."
          />
        </div>

        <div className="intelligence-section">
          <h3>Blockers</h3>
          <EvidenceList
            items={verdict?.blockers}
            emptyMessage="No blockers were included in the response."
          />
        </div>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Execution details</h2>
            <p>Actual metadata returned for this run.</p>
          </div>
        </div>

        <div className="intelligence-metadata-grid">
          <Metadata label="Execution mode" value={result?.mode} />
          <Metadata label="Model calls" value={result?.modelCalls} />
          <Metadata label="Tool rounds" value={result?.toolRounds} />
          <Metadata
            label="Elapsed time"
            value={
              typeof result?.elapsedMs === 'number'
                ? `${result.elapsedMs} ms`
                : undefined
            }
          />
          <Metadata label="Prompt tokens" value={result?.promptTokens} />
          <Metadata
            label="Completion tokens"
            value={result?.completionTokens}
          />
          <Metadata
            label="Timestamp"
            value={
              result?.timestamp
                ? new Date(result.timestamp).toLocaleString()
                : undefined
            }
          />
        </div>

        {result?.note && (
          <div className="intelligence-note">
            <strong>Execution note</strong>
            <p>{result.note}</p>
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Agent trace</h2>
            <p>Tool calls included in the response.</p>
          </div>
          <span className="score-chip">
            {Array.isArray(result?.trace) ? result.trace.length : 0} steps
          </span>
        </div>

        {!Array.isArray(result?.trace) || result.trace.length === 0 ? (
          <EmptyList>
            No trace steps were returned for this run.
          </EmptyList>
        ) : (
          <div className="intelligence-trace">
            {result.trace.map((step, index) => (
              <article
                className="intelligence-trace-step"
                key={`${step.round}-${step.tool}-${index}`}
              >
                <div className="intelligence-trace-heading">
                  <strong>{step.tool || 'Unnamed tool'}</strong>
                  <span className="score-chip">
                    Round {step.round ?? '—'}
                  </span>
                </div>

                <div className="intelligence-metadata-grid">
                  <Metadata
                    label="Model time"
                    value={
                      typeof step.modelMs === 'number'
                        ? `${step.modelMs} ms`
                        : undefined
                    }
                  />
                  <Metadata
                    label="Tool round time"
                    value={
                      typeof step.toolRoundMs === 'number'
                        ? `${step.toolRoundMs} ms`
                        : 'Not supplied'
                    }
                  />
                </div>

                <details className="intelligence-arguments">
                  <summary>Tool arguments</summary>
                  <pre>{step.arguments || 'No arguments supplied.'}</pre>
                </details>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function NotesResults({ result }) {
  const notes = result?.notes

  return (
    <div className="intelligence-results">
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>{notes?.title || 'Generated release notes'}</h2>
            <p>Review this draft before using it in a release.</p>
          </div>
          <span className="status-pill neutral">
            <span className="status-dot" />
            Draft
          </span>
        </div>

        <p className="intelligence-summary">
          {notes?.summary || 'No summary was supplied.'}
        </p>

        <div className="intelligence-section">
          <h3>Features</h3>
          <EvidenceList
            items={notes?.features}
            emptyMessage="No features listed."
          />
        </div>

        <div className="intelligence-section">
          <h3>Fixes</h3>
          <EvidenceList
            items={notes?.fixes}
            emptyMessage="No fixes listed."
          />
        </div>

        <div className="intelligence-section">
          <h3>Maintenance</h3>
          <EvidenceList
            items={notes?.chores}
            emptyMessage="No maintenance items listed."
          />
        </div>

        <div className="intelligence-section">
          <h3>Breaking changes</h3>
          <EvidenceList
            items={notes?.breakingChanges}
            emptyMessage="No breaking changes listed."
          />
        </div>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Source and validation</h2>
            <p>Metadata returned by the release-notes endpoint.</p>
          </div>
        </div>

        <div className="intelligence-metadata-grid">
          <Metadata label="Baseline" value={result?.baseline} />
          <Metadata label="Pull requests" value={result?.prCount} />
          <Metadata label="Model calls" value={result?.modelCalls} />
          <Metadata
            label="Elapsed time"
            value={
              typeof result?.elapsedMs === 'number'
                ? `${result.elapsedMs} ms`
                : undefined
            }
          />
          <Metadata label="Prompt tokens" value={result?.promptTokens} />
          <Metadata
            label="Completion tokens"
            value={result?.completionTokens}
          />
          <Metadata
            label="Timestamp"
            value={
              result?.timestamp
                ? new Date(result.timestamp).toLocaleString()
                : undefined
            }
          />
        </div>

        <div className="intelligence-section">
          <h3>Invalid PR references</h3>
          <EvidenceList
            items={result?.invalidReferences?.map(String)}
            emptyMessage="No invalid references were reported."
          />
        </div>

        <div className="intelligence-section">
          <h3>Omitted PRs</h3>
          <EvidenceList
            items={result?.omittedPrs?.map(String)}
            emptyMessage="No omitted pull requests were reported."
          />
        </div>

        <p className="intelligence-muted">
          Validate the generated notes against the repository before using
          them in a release.
        </p>
      </section>
    </div>
  )
}

export default function ReleaseIntelligence({ api, mode }) {
  const [activeTab, setActiveTab] = useState('check')
  const [checkResult, setCheckResult] = useState(null)
  const [notesResult, setNotesResult] = useState(null)
  const [checkState, setCheckState] = useState('idle')
  const [notesState, setNotesState] = useState('idle')
  const [checkError, setCheckError] = useState(null)
  const [notesError, setNotesError] = useState(null)

  async function runReleaseCheck() {
    setCheckState('loading')
    setCheckError(null)

    try {
      const result = await api.releaseCheck()
      setCheckResult(result)
      setCheckState('ready')
    } catch (error) {
      setCheckError(error)
      setCheckState('error')
    }
  }

  async function runReleaseNotes() {
    setNotesState('loading')
    setNotesError(null)

    try {
      const result = await api.releaseNotes()
      setNotesResult(result)
      setNotesState('ready')
    } catch (error) {
      setNotesError(error)
      setNotesState('error')
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">RELEASE OPERATIONS</p>
          <h1>Release intelligence</h1>
          <p className="page-description">
            Inspect readiness evidence, generate release notes, and review
            the execution details returned by the agent.
          </p>
        </div>
      </div>

      <div className={`notice-banner ${mode === 'demo' ? 'notice-demo' : 'notice-live'}`}>
        <span className="notice-icon">{mode === 'demo' ? 'i' : '✓'}</span>
        <div>
          <strong>
            {mode === 'demo' ? 'Demo mode' : 'Live API mode'}
          </strong>
          <p>
            {mode === 'demo'
              ? 'Responses are examples from the demo adapter, not live repository analysis.'
              : 'Requests call the configured backend. These endpoints run analysis but do not approve or publish releases.'}
          </p>
        </div>
      </div>

      <div className="intelligence-tabs" role="tablist" aria-label="Release intelligence views">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'check'}
          className={activeTab === 'check' ? 'active' : ''}
          onClick={() => setActiveTab('check')}
        >
          Release check
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'notes'}
          className={activeTab === 'notes' ? 'active' : ''}
          onClick={() => setActiveTab('notes')}
        >
          Release notes
        </button>
      </div>

      {activeTab === 'check' && (
        <section className="intelligence-tab-content" role="tabpanel">
          <div className="panel intelligence-action-panel">
            <div>
              <h2>Run release check</h2>
              <p>
                Collect release evidence and request a readiness verdict.
              </p>
            </div>
            <button
              type="button"
              className="button button-primary"
              onClick={runReleaseCheck}
              disabled={checkState === 'loading'}
            >
              {checkState === 'loading' ? 'Checking…' : 'Run release check'}
            </button>
          </div>

          {checkState === 'loading' && (
            <Loading label="Waiting for readiness analysis…" />
          )}

          {checkState === 'error' && (
            <ErrorPanel error={checkError} onRetry={runReleaseCheck} />
          )}

          {checkState === 'ready' && checkResult && (
            <ReadinessResults result={checkResult} />
          )}

          {checkState === 'idle' && (
            <div className="empty-state">
              <strong>No readiness result yet</strong>
              <p>Run a release check to display the returned verdict and evidence.</p>
            </div>
          )}
        </section>
      )}

      {activeTab === 'notes' && (
        <section className="intelligence-tab-content" role="tabpanel">
          <div className="panel intelligence-action-panel">
            <div>
              <h2>Generate release notes</h2>
              <p>
                Generate a draft from the repository evidence gathered by the backend.
              </p>
            </div>
            <button
              type="button"
              className="button button-primary"
              onClick={runReleaseNotes}
              disabled={notesState === 'loading'}
            >
              {notesState === 'loading' ? 'Generating…' : 'Generate notes'}
            </button>
          </div>

          {notesState === 'loading' && (
            <Loading label="Generating release notes…" />
          )}

          {notesState === 'error' && (
            <ErrorPanel error={notesError} onRetry={runReleaseNotes} />
          )}

          {notesState === 'ready' && notesResult && (
            <NotesResults result={notesResult} />
          )}

          {notesState === 'idle' && (
            <div className="empty-state">
              <strong>No release notes yet</strong>
              <p>Generate notes to inspect the returned draft and validation metadata.</p>
            </div>
          )}
        </section>
      )}
    </>
  )
}