import { useCallback, useEffect, useMemo, useState } from 'react'
import { getApi } from './api/index.js'
import { describeApiError } from './api/errors.js'
import { clearAccessToken, setAccessToken } from './api/http.js'

const navigation = [
  { group: 'WORKSPACE', items: [
    { id: 'overview', label: 'Overview', icon: '▦' },
    { id: 'drafts', label: 'Release drafts', icon: '▤' },
    { id: 'intelligence', label: 'Release intelligence', icon: '⌁' },
    { id: 'ask', label: 'Ask AI', icon: '✳' },
    { id: 'knowledge', label: 'Knowledge base', icon: '▧' },
    { id: 'users', label: 'Users & access', icon: '♙' },
  ] },
  { group: 'PREFERENCES', items: [
    { id: 'settings', label: 'Settings', icon: '⚙' },
    { id: 'account', label: 'Account & sign in', icon: '⇥' },
  ] },
]
const screens = Object.fromEntries(navigation.flatMap((group) => group.items.map((item) => [item.id, item.label])))
const palette = [
  { name: 'Blue', value: '#2563EB', rgb: '37, 99, 235' },
  { name: 'Violet', value: '#7C3AED', rgb: '124, 58, 237' },
  { name: 'Teal', value: '#0D9488', rgb: '13, 148, 136' },
  { name: 'Emerald', value: '#16A34A', rgb: '22, 163, 74' },
  { name: 'Rose', value: '#E11D48', rgb: '225, 29, 72' },
  { name: 'Amber', value: '#D97706', rgb: '217, 119, 6' },
  { name: 'Indigo', value: '#4F46E5', rgb: '79, 70, 229' },
  { name: 'Cyan', value: '#0891B2', rgb: '8, 145, 178' },
]
const initialTheme = localStorage.getItem('rp-theme') || 'system'
const initialAccent = localStorage.getItem('rp-accent') || '#2563EB'
const initialMode = localStorage.getItem('rp-mode') || import.meta.env.VITE_DEFAULT_MODE || 'demo'

function applyAppearance(theme, accent) {
  const selected = palette.find((item) => item.value === accent) || palette[0]
  document.documentElement.dataset.theme = theme
  document.documentElement.style.setProperty('--accent', selected.value)
  document.documentElement.style.setProperty('--accent-rgb', selected.rgb)
}
function Icon({ children }) { return <span className="nav-icon" aria-hidden="true">{children}</span> }
function StatusPill({ kind = 'neutral', children }) { return <span className={`status-pill ${kind}`}><span className="status-dot" />{children}</span> }
function PageHeading({ eyebrow = 'RELEASE OPERATIONS', title, description, actions }) {
  return <div className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p className="page-description">{description}</p></div>{actions && <div className="heading-actions">{actions}</div>}</div>
}
function Loading({ label = 'Loading data…' }) { return <div className="inline-state"><span className="spinner" />{label}</div> }
function ErrorState({ error, onRetry }) { return <div className="state-card error-state"><span className="state-symbol">!</span><div><strong>Couldn’t load this data</strong><p>{describeApiError(error)}</p>{onRetry && <button className="button button-secondary" onClick={onRetry}>Try again</button>}</div></div> }

export default function App() {
  const [screen, setScreen] = useState('overview')
  const [mode, setModeState] = useState(initialMode === 'live' ? 'live' : 'demo')
  const [theme, setTheme] = useState(initialTheme)
  const [accent, setAccent] = useState(initialAccent)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [connection, setConnection] = useState({ state: 'checking', label: 'Checking…' })
  const [drafts, setDrafts] = useState([])
  const [draftsState, setDraftsState] = useState('idle')
  const [draftsError, setDraftsError] = useState(null)
  const [toast, setToast] = useState('')
  const [query, setQuery] = useState('')
  const [, setKnowledge] = useState(null)
  const [knowledgeState, setKnowledgeState] = useState('idle')
  const [knowledgeError, setKnowledgeError] = useState(null)
  const [knowledgeQuery, setKnowledgeQuery] = useState('deployment rollback')
  const [knowledgeResults, setKnowledgeResults] = useState(null)
  const [askInput, setAskInput] = useState('')
  const [messages, setMessages] = useState([{ role: 'assistant', text: 'Welcome to ReleasePilot. Ask about release readiness, release notes or a runbook. Demo responses are labelled and do not call a live model.' }])
  const [askBusy, setAskBusy] = useState(false)
  const api = useMemo(() => getApi(mode), [mode])

  const notify = useCallback((message) => {
    setToast(message)
    window.setTimeout(() => setToast(''), 2600)
  }, [])
  const refreshConnection = useCallback(async () => {
    if (mode === 'demo') {
      setConnection({ state: 'demo', label: 'Demo mode' })
      return
    }
    setConnection({ state: 'checking', label: 'Checking backend…' })
    try {
      await api.health()
      setConnection({ state: 'connected', label: 'Backend connected' })
    } catch (error) {
      setConnection({ state: 'disconnected', label: 'Backend unreachable', error })
    }
  }, [api, mode])
  const loadDrafts = useCallback(async () => {
    setDraftsState('loading'); setDraftsError(null)
    try { setDrafts(await api.listDrafts()); setDraftsState('ready') }
    catch (error) { setDraftsError(error); setDraftsState('error') }
  }, [api])
  const loadKnowledgeStatus = useCallback(async () => {
    setKnowledgeState('loading'); setKnowledgeError(null)
    try { setKnowledge(await api.knowledgeStatus()); setKnowledgeState('ready') }
    catch (error) { setKnowledgeError(error); setKnowledgeState('error') }
  }, [api])

  useEffect(() => { applyAppearance(theme, accent); localStorage.setItem('rp-theme', theme); localStorage.setItem('rp-accent', accent) }, [theme, accent])
  useEffect(() => { localStorage.setItem('rp-mode', mode); refreshConnection(); loadDrafts(); loadKnowledgeStatus() }, [mode, refreshConnection, loadDrafts, loadKnowledgeStatus])
  useEffect(() => { if (theme !== 'system') return undefined; const media = window.matchMedia('(prefers-color-scheme: dark)'); const listener = () => applyAppearance('system', accent); media.addEventListener('change', listener); return () => media.removeEventListener('change', listener) }, [theme, accent])
  useEffect(() => { if (!toast) return undefined; const timer = window.setTimeout(() => setToast(''), 2600); return () => window.clearTimeout(timer) }, [toast])

  function navigate(id) { setScreen(id); setSidebarOpen(false) }
  async function searchKnowledge(event) {
    event.preventDefault(); if (!knowledgeQuery.trim()) return
    setKnowledgeState('searching'); setKnowledgeError(null)
    try { setKnowledgeResults(await api.searchKnowledge(knowledgeQuery.trim())); setKnowledgeState('results') }
    catch (error) { setKnowledgeError(error); setKnowledgeState('error') }
  }
  async function askRelease(event) {
    event.preventDefault(); const text = askInput.trim(); if (!text || askBusy) return
    setMessages((previous) => [...previous, { role: 'user', text }]); setAskInput(''); setAskBusy(true)
    try { const response = await api.ask(text); setMessages((previous) => [...previous, { role: 'assistant', text: response?.reply || 'The API returned no reply.' }]) }
    catch (error) { setMessages((previous) => [...previous, { role: 'error', text: describeApiError(error) }]) }
    finally { setAskBusy(false) }
  }
  async function signIn(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      const response = await api.login(String(form.get('username')), String(form.get('password')))
      setAccessToken(response.token); notify('Signed in successfully'); navigate('overview')
      await refreshConnection()
    } catch (error) { notify(describeApiError(error)) }
  }
  function changeMode(nextMode) {
    if (nextMode === mode) return
    clearAccessToken(); setModeState(nextMode); setConnection({ state: nextMode === 'demo' ? 'demo' : 'checking', label: nextMode === 'demo' ? 'Demo mode' : 'Checking backend…' })
    notify(nextMode === 'demo' ? 'Demo mode selected' : 'Live mode selected; checking actual backend health')
  }

  const visibleDrafts = drafts.filter((draft) => `${draft.tag || ''} ${draft.title || ''} ${draft.status || ''}`.toLowerCase().includes(query.toLowerCase()))
  return <div className="app-shell">
    {sidebarOpen && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setSidebarOpen(false)} />}
    <aside className={`sidebar ${sidebarOpen ? 'sidebar-open' : ''}`}>
      <div className="brand"><span className="brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><path d="M5 17V7l7-4 7 4v10l-7 4-7-4Z"/><path d="m8 12 2.5 2.5L16 9"/></svg></span><span>ReleasePilot</span></div>
      {navigation.map((group) => <div className="nav-group" key={group.group}><p className="nav-group-title">{group.group}</p><nav aria-label={group.group}>{group.items.map((item) => <button key={item.id} className={`nav-item ${screen === item.id ? 'active' : ''}`} onClick={() => navigate(item.id)} aria-current={screen === item.id ? 'page' : undefined}><Icon>{item.icon}</Icon><span>{item.label}</span>{item.id === 'drafts' && draftsState === 'ready' && <span className="nav-count">{drafts.length}</span>}</button>)}</nav></div>)}
      <div className="sidebar-bottom"><div className="sidebar-connection"><span className={`connection-dot ${connection.state}`} /><div><strong>{mode === 'demo' ? 'Demo environment' : connection.label}</strong><small>{mode === 'demo' ? 'Recorded sample data · No live writes' : connection.state === 'connected' ? 'Spring Boot API responded' : connection.state === 'disconnected' ? 'Check backend and API URL' : 'Verifying actual health endpoint'}</small></div></div><div className="sidebar-footer">ReleasePilot <span>Frontend foundation</span></div></div>
    </aside>
    <main className="main-panel">
      <header className="topbar"><div className="topbar-left"><button className="icon-button mobile-menu" aria-label="Open navigation" onClick={() => setSidebarOpen(true)}>☰</button><div className="breadcrumb"><span>Workspace</span><span className="breadcrumb-separator">/</span><strong>{screens[screen]}</strong></div></div><div className="topbar-actions"><label className="mode-select-label"><span className="sr-only">Connection mode</span><select className="mode-select" value={mode} onChange={(event) => changeMode(event.target.value)}><option value="demo">Demo mode</option><option value="live">Live mode</option></select></label><span className={`top-connection ${connection.state}`} title={connection.label}><span className="connection-dot" />{mode === 'demo' ? 'Sample data' : connection.state === 'connected' ? 'Connected' : connection.state === 'checking' ? 'Checking' : 'Disconnected'}</span><button className="icon-button theme-toggle" aria-label="Cycle appearance theme" title={`Theme: ${theme}`} onClick={() => setTheme(theme === 'system' ? 'light' : theme === 'light' ? 'dark' : 'system')}>{theme === 'dark' ? '☾' : theme === 'light' ? '☼' : '◐'}</button><button className="user-avatar" aria-label="Open account screen" onClick={() => navigate('account')}>RP</button></div></header>
      <div className="page-content">
        {screen === 'overview' && <>
          <PageHeading title="Release overview" description="A clear view of release work, current evidence and the next step." actions={<><button className="button button-secondary" onClick={() => { refreshConnection(); loadDrafts(); loadKnowledgeStatus(); notify('Refreshing workspace data') }}>↻ Refresh</button><button className="button button-primary" onClick={() => navigate('drafts')}>View release drafts <span aria-hidden="true">→</span></button></>} />
          <div className={`notice-banner ${mode === 'demo' ? 'notice-demo' : connection.state === 'connected' ? 'notice-live' : 'notice-warning'}`}><span className="notice-icon">{mode === 'demo' ? 'i' : connection.state === 'connected' ? '✓' : '!'}</span><div><strong>{mode === 'demo' ? 'You’re exploring Demo mode' : connection.state === 'connected' ? 'Live backend responded to health check' : connection.state === 'checking' ? 'Checking your backend connection' : 'Live backend is not reachable'}</strong><p>{mode === 'demo' ? 'This mode uses clearly labelled sample responses. It does not approve or publish releases.' : connection.state === 'connected' ? 'Read-only data is loaded from the configured API. No release action has been performed.' : connection.state === 'checking' ? 'A green connection state appears only after the configured health endpoint responds.' : describeApiError(connection.error)}</p></div>{mode === 'live' && <button className="button button-secondary" onClick={refreshConnection}>Check again</button>}</div>
          <div className="section-heading"><div><h2>Release workspace</h2><p>Live counts will be derived from the API response.</p></div><StatusPill kind={mode === 'demo' ? 'neutral' : connection.state === 'connected' ? 'success' : 'warning'}>{mode === 'demo' ? 'Demo data' : connection.label}</StatusPill></div>
          <div className="metric-grid"><article className="metric-card"><div className="metric-label">Draft records <span className="metric-icon">▤</span></div><div className="metric-value">{draftsState === 'ready' ? drafts.length : '—'}</div><p>{draftsState === 'loading' ? 'Loading records…' : draftsState === 'error' ? 'Could not load drafts' : 'Records returned by the draft endpoint'}</p></article><article className="metric-card"><div className="metric-label">Readiness verdict <span className="metric-icon">⌁</span></div><div className="metric-value metric-word">Not run</div><p>Run a release check to get a real verdict.</p></article><article className="metric-card"><div className="metric-label">Knowledge index <span className="metric-icon">▧</span></div><div className="metric-value metric-word">{knowledgeState === 'ready' ? 'Available' : knowledgeState === 'error' ? 'Unknown' : 'Checking'}</div><p>{mode === 'demo' ? 'Recorded demo status' : 'Status from knowledge API'}</p></article><article className="metric-card"><div className="metric-label">Connection <span className="metric-icon">⌁</span></div><div className="metric-value metric-word">{mode === 'demo' ? 'Demo' : connection.state === 'connected' ? 'Online' : connection.state === 'checking' ? 'Checking' : 'Offline'}</div><p>{mode === 'demo' ? 'No backend required' : 'Based on the health endpoint'}</p></article></div>
          <div className="content-grid"><section className="panel"><div className="panel-heading"><div><h2>Recent release drafts</h2><p>Release records from the current mode.</p></div><button className="text-button" onClick={() => navigate('drafts')}>View all →</button></div>{draftsState === 'loading' && <Loading label="Loading draft records…" />}{draftsState === 'error' && <ErrorState error={draftsError} onRetry={loadDrafts} />}{draftsState === 'ready' && drafts.length === 0 && <div className="empty-state"><strong>No release drafts yet</strong><p>When the API returns drafts, they’ll appear here.</p></div>}{draftsState === 'ready' && drafts.slice(0, 4).map((draft) => <button className="draft-list-row" key={draft.id} onClick={() => { setQuery(draft.tag || ''); navigate('drafts') }}><span className="draft-file-icon">▤</span><span className="draft-copy"><strong>{draft.title || draft.tag || `Draft #${draft.id}`}</strong><small>{draft.tag || 'No tag'} · {draft.createdAt ? new Date(draft.createdAt).toLocaleString() : 'Date unavailable'}</small></span><StatusPill kind={String(draft.verdictStatus || draft.status).toLowerCase().includes('ready') ? 'success' : String(draft.status).toLowerCase().includes('block') ? 'danger' : 'neutral'}>{draft.verdictStatus || draft.status || 'Unknown'}</StatusPill><span className="row-chevron">›</span></button>)}</section>
            <section className="panel"><div className="panel-heading"><div><h2>Quick actions</h2><p>Start with evidence, not assumptions.</p></div></div><button className="quick-action" onClick={() => navigate('intelligence')}><span className="quick-icon">⌁</span><span><strong>Release intelligence</strong><small>Readiness, notes and agent trace</small></span><span>→</span></button><button className="quick-action" onClick={() => navigate('knowledge')}><span className="quick-icon">▧</span><span><strong>Search knowledge</strong><small>Look up runbooks and release guidance</small></span><span>→</span></button><button className="quick-action" onClick={() => navigate('ask')}><span className="quick-icon">✳</span><span><strong>Ask AI</strong><small>Ask a question using the supported agent API</small></span><span>→</span></button><div className="truth-note"><strong>Approval is not publication.</strong><p>Release actions will be added after read-only screens and backend contracts are verified.</p></div></section></div>
        </>}
        {screen === 'settings' && <><PageHeading title="Settings" description="Personalize the workspace and choose how it connects to ReleasePilot." /><div className="settings-layout"><section className="panel"><div className="panel-heading"><div><h2>Appearance</h2><p>These preferences are stored on this device and are independent of your account.</p></div></div><div className="setting-row"><div><strong>Theme</strong><p>Choose light, dark or follow your system preference.</p></div><div className="choice-group">{['light', 'dark', 'system'].map((value) => <button key={value} className={`choice-button ${theme === value ? 'selected' : ''}`} onClick={() => setTheme(value)}>{value === 'light' ? '☼' : value === 'dark' ? '☾' : '◐'} <span>{value[0].toUpperCase() + value.slice(1)}</span></button>)}</div></div><div className="setting-row"><div><strong>Dashboard accent colour</strong><p>Changes primary controls, active navigation, links and focus rings. Status colours stay semantic.</p></div><div className="palette-control"><div className="accent-palette" role="group" aria-label="Dashboard accent colour">{palette.map((colour) => <button key={colour.value} className={`accent-swatch ${accent === colour.value ? 'selected' : ''}`} style={{ '--swatch': colour.value }} aria-label={`${colour.name} accent colour`} aria-pressed={accent === colour.value} title={colour.name} onClick={() => { setAccent(colour.value); notify(`${colour.name} accent selected`) }}>{accent === colour.value && <span>✓</span>}</button>)}</div><p className="palette-label">{palette.find((colour) => colour.value === accent)?.name || 'Blue'} <span>·</span> {accent}</p></div></div><div className="setting-row preview-setting"><div><strong>Preview</strong><p>Example controls using your selected accent.</p></div><div className="preview-actions"><button className="button button-primary">Primary action</button><button className="button button-accent-soft">Selected state</button></div></div></section><section className="panel"><div className="panel-heading"><div><h2>Connection mode</h2><p>Demo is safe to explore. Live uses your configured Spring Boot backend.</p></div><StatusPill kind={mode === 'demo' ? 'neutral' : connection.state === 'connected' ? 'success' : 'warning'}>{mode === 'demo' ? 'Demo' : connection.label}</StatusPill></div><div className="setting-row"><div><strong>Demo mode</strong><p>Recorded sample responses; no real release writes.</p></div><button className={`button ${mode === 'demo' ? 'button-primary' : 'button-secondary'}`} onClick={() => changeMode('demo')}>{mode === 'demo' ? 'Selected' : 'Use Demo'}</button></div><div className="setting-row"><div><strong>Live mode</strong><p>Calls the configured API. A failed health check is shown as disconnected.</p></div><button className={`button ${mode === 'live' ? 'button-primary' : 'button-secondary'}`} onClick={() => changeMode('live')}>{mode === 'live' ? 'Selected' : 'Use Live'}</button></div><div className="setting-row"><div><strong>API base URL</strong><p>Configured with the Vite environment variable.</p></div><code className="code-chip">VITE_API_BASE_URL</code></div><div className="setting-row"><div><strong>Connection health</strong><p>{connection.state === 'connected' ? 'The backend health endpoint responded.' : mode === 'demo' ? 'Demo mode does not require a backend.' : connection.state === 'checking' ? 'A health check is in progress.' : describeApiError(connection.error)}</p></div><button className="button button-secondary" onClick={refreshConnection} disabled={mode === 'demo'}>Check connection</button></div></section></div></>}
        {screen === 'drafts' && <><PageHeading title="Release drafts" description="Browse the release records returned by the selected API adapter." actions={<button className="button button-secondary" onClick={loadDrafts}>↻ Refresh</button>} /><div className="panel"><div className="list-toolbar"><label className="search-field"><span aria-hidden="true">⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search tag, title or status…" aria-label="Search release drafts" /></label><span className="result-count">{draftsState === 'ready' ? `${visibleDrafts.length} records` : '—'}</span></div>{draftsState === 'loading' && <Loading />}{draftsState === 'error' && <ErrorState error={draftsError} onRetry={loadDrafts} />}{draftsState === 'ready' && <div className="responsive-table-wrap"><table className="data-table"><thead><tr><th>Release</th><th>Status</th><th>Readiness</th><th>Created</th><th>Created by</th></tr></thead><tbody>{visibleDrafts.map((draft) => <tr key={draft.id}><td><strong>{draft.title || draft.tag || `Draft #${draft.id}`}</strong><small>{draft.tag || 'Tag unavailable'}{draft.prerelease ? ' · Prerelease' : ''}</small></td><td>{draft.status || 'Unknown'}</td><td><StatusPill kind={draft.verdictStatus === 'READY' ? 'success' : draft.verdictStatus === 'NOT_READY' ? 'danger' : 'neutral'}>{draft.verdictStatus || 'Unknown'}</StatusPill></td><td>{draft.createdAt ? new Date(draft.createdAt).toLocaleString() : '—'}</td><td>{draft.createdBy || 'Not provided'}</td></tr>)}</tbody></table>{visibleDrafts.length === 0 && <div className="empty-state"><strong>No matching drafts</strong><p>Try another search term.</p></div>}</div>}<p className="panel-footnote">Phase 14.1 is read-only. Draft creation and approval will be added after the read-only workflow is verified.</p></div></>}
        {screen === 'knowledge' && <><PageHeading title="Knowledge base" description="Search the backend's indexed runbooks and release documentation." actions={<button className="button button-secondary" onClick={loadKnowledgeStatus}>↻ Refresh status</button>} /><div className="metric-grid knowledge-metrics"><article className="metric-card"><div className="metric-label">Index status</div><div className="metric-value metric-word">{knowledgeState === 'ready' ? 'Available' : knowledgeState === 'error' ? 'Unknown' : knowledgeState === 'loading' ? 'Checking' : '—'}</div><p>{mode === 'demo' ? 'Recorded demo status' : 'Actual knowledge status endpoint'}</p></article><article className="metric-card"><div className="metric-label">Search results</div><div className="metric-value">{knowledgeResults?.hits?.length ?? '—'}</div><p>Results from your most recent query</p></article></div><section className="panel"><div className="panel-heading"><div><h2>Search documentation</h2><p>Source labels and scores come from the selected adapter.</p></div></div><form className="search-form" onSubmit={searchKnowledge}><label className="search-field"><span aria-hidden="true">⌕</span><input value={knowledgeQuery} onChange={(event) => setKnowledgeQuery(event.target.value)} placeholder="Search deployment, rollback, release policy…" aria-label="Knowledge base query" /></label><button className="button button-primary" disabled={knowledgeState === 'searching' || !knowledgeQuery.trim()}>{knowledgeState === 'searching' ? 'Searching…' : 'Search'}</button></form>{knowledgeState === 'error' && <ErrorState error={knowledgeError} onRetry={() => searchKnowledge({ preventDefault() {} })} />}{knowledgeResults && knowledgeState !== 'error' && <div className="knowledge-results">{knowledgeResults.hits?.length ? knowledgeResults.hits.map((hit, index) => <article className="knowledge-result" key={`${hit.source}-${hit.heading}-${index}`}><div className="knowledge-result-top"><h3>{hit.heading || hit.source || `Result ${index + 1}`}</h3>{typeof hit.score === 'number' && <span className="score-chip">{hit.score.toFixed(2)} score</span>}</div><p>{hit.text || 'No snippet provided by the API.'}</p><small>{hit.source || 'Source not provided'}</small></article>) : <div className="empty-state"><strong>No matching knowledge found</strong><p>Try a broader query or check the index status.</p></div>}</div>}</section></>}
        {screen === 'ask' && <><PageHeading title="Ask AI" description="Ask release questions through the supported agent endpoint." /><div className="chat-layout"><section className="panel chat-panel"><div className="panel-heading"><div><h2>Release assistant</h2><p>{mode === 'demo' ? 'Demo replies are static examples, not model-generated.' : 'Responses come from /api/agent/ask.'}</p></div><StatusPill kind={mode === 'demo' ? 'neutral' : 'success'}>{mode === 'demo' ? 'Demo' : 'Live API'}</StatusPill></div><div className="chat-messages" aria-live="polite">{messages.map((message, index) => <div className={`chat-message ${message.role}`} key={`${message.role}-${index}`}><span className="chat-avatar">{message.role === 'user' ? 'You' : 'AI'}</span><p>{message.text}</p></div>)}{askBusy && <div className="chat-message"><span className="chat-avatar">AI</span><p><Loading label="Waiting for response…" /></p></div>}</div><form className="chat-composer" onSubmit={askRelease}><textarea value={askInput} onChange={(event) => setAskInput(event.target.value)} placeholder="Ask about a release, blocker or runbook…" aria-label="Your question" rows={2} /><button className="button button-primary" disabled={askBusy || !askInput.trim()}>{askBusy ? 'Sending…' : 'Send ↑'}</button></form></section><aside className="panel"><div className="panel-heading"><div><h2>Suggested prompts</h2><p>Choose a starting point.</p></div></div>{['What should I verify before approving a release?', 'Summarize the release rollback procedure.', 'Explain the difference between READY and UNKNOWN.'].map((prompt) => <button className="suggested-prompt" key={prompt} onClick={() => setAskInput(prompt)}>{prompt}<span>↗</span></button>)}<div className="truth-note"><strong>Evidence first</strong><p>We’ll show citations and traces only when the backend returns them.</p></div></aside></div></>}
        {screen === 'intelligence' && <><PageHeading title="Release intelligence" description="The workspace for readiness verdicts, release notes and agent execution traces." /><div className="phase-placeholder"><span className="placeholder-icon">⌁</span><p className="eyebrow">PHASE 14.3</p><h2>Release intelligence is next</h2><p>Phase 14.1 establishes the shell and API adapters. The next phase will wire readiness, release notes, verdict evidence and agent traces to the verified backend responses.</p><button className="button button-primary" onClick={async () => { try { const result = await api.releaseCheck(); notify(`Readiness response received: ${result.verdict || 'verdict not supplied'}`) } catch (error) { notify(describeApiError(error)) } }}>Test release-check endpoint</button><small>In Demo mode this returns an explicitly labelled sample. In Live mode it calls the real endpoint.</small></div></>}
        {screen === 'users' && <><PageHeading title="Users & access" description="User administration is available only to authorized administrators." /><div className="phase-placeholder"><span className="placeholder-icon">♙</span><p className="eyebrow">PHASE 14.4</p><h2>Permission-aware user management</h2><p>The backend exposes admin user endpoints. The full interface will be added with authentication and authorization handling in Phase 14.4.</p><button className="button button-secondary" onClick={() => navigate('account')}>Open account & sign in</button></div></>}
        {screen === 'account' && <><PageHeading title="Account & sign in" description="Authenticate against the backend in Live mode. Tokens are kept in memory only." /><div className="account-layout"><section className="panel sign-in-panel"><div className="panel-heading"><div><h2>Sign in to ReleasePilot</h2><p>Uses POST /api/auth/login.</p></div></div><form className="sign-in-form" onSubmit={signIn}><label>Username<input name="username" autoComplete="username" required placeholder="Your account username" disabled={mode === 'demo'} /></label><label>Password<input name="password" type="password" autoComplete="current-password" required placeholder="Your password" disabled={mode === 'demo'} /></label><button className="button button-primary" disabled={mode === 'demo'}>Sign in</button>{mode === 'demo' && <p className="form-note">Switch to Live mode to enable backend authentication. No credentials are stored.</p>}</form></section><section className="panel"><div className="panel-heading"><div><h2>Session safety</h2><p>Frontend foundation security defaults.</p></div></div><ul className="security-list"><li>JWT is held in memory, not local storage.</li><li>Signing out clears the in-memory token.</li><li>Backend authorization remains authoritative.</li><li>A page reload requires signing in again.</li></ul><button className="button button-secondary" onClick={() => { clearAccessToken(); notify('Local session token cleared') }}>Clear in-memory session</button></section></div></>}
      </div>
      <footer className="app-footer"><span>ReleasePilot</span><span>Phase 14.1 · Frontend foundation</span><button onClick={() => navigate('settings')}>Appearance settings</button></footer>
    </main>
    {toast && <div className="toast-message" role="status">{toast}</div>}
  </div>
}
