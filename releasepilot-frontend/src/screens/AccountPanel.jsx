
import { useEffect, useState } from 'react'
import {
  clearAccessToken,
  getAccessToken,
  getTokenExpiresAt,
  setAccessToken,
} from '../api/http.js'
import { describeApiError } from '../api/errors.js'

export default function AccountPanel({ api, mode, notify }) {
  const [account, setAccount] = useState(null)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [expiresAt, setExpiresAt] = useState(getTokenExpiresAt())

//   async function loadAccount() {
//     if (mode !== 'live' || !getAccessToken()) {
//       setAccount(null)
//       setExpiresAt(null)
//       return
//     }
//
//     try {
//       const result = await api.me()
//       setAccount(result)
//       setExpiresAt(getTokenExpiresAt())
//     } catch (requestError) {
//       setAccount(null)
//       setError(describeApiError(requestError))
//     }
//   }

  useEffect(() => {
    let cancelled = false

    Promise.resolve().then(async () => {
      if (mode !== 'live' || !getAccessToken()) {
        if (!cancelled) {
          setAccount(null)
          setExpiresAt(null)
        }
        return
      }

      try {
        const result = await api.me()

        if (!cancelled) {
          setAccount(result)
          setExpiresAt(getTokenExpiresAt())
        }
      } catch (requestError) {
        if (!cancelled) {
          setAccount(null)
          setError(describeApiError(requestError))
        }
      }
    })

    return () => {
      cancelled = true
    }
  }, [api, mode])

  useEffect(() => {
    function onExpired() {
      setAccount(null)
      setExpiresAt(null)
      setError('Your session expired. Please sign in again.')
      setMessage('')
    }

    window.addEventListener('releasepilot:session-expired', onExpired)

    return () => {
      window.removeEventListener('releasepilot:session-expired', onExpired)
    }
  }, [])

  async function signIn(event) {
    event.preventDefault()
    if (busy || mode !== 'live') return

    setBusy(true)
    setError('')
    setMessage('')

    try {
      const result = await api.login(username.trim(), password)

      if (!result?.token) {
        throw new Error('The login response did not contain a token.')
      }

      setAccessToken(result.token, result.expiresAt || null)
      setPassword('')
      setAccount({
        username: result.username || username.trim(),
        roles: Array.isArray(result.roles) ? result.roles : [],
      })
      setExpiresAt(result.expiresAt ? new Date(result.expiresAt).getTime() : null)
      setMessage('You are signed in.')
      notify?.('Signed in successfully')
    } catch (requestError) {
      setError(describeApiError(requestError))
    } finally {
      setBusy(false)
    }
  }

  function signOut() {
    clearAccessToken()
    setAccount(null)
    setExpiresAt(null)
    setCurrentPassword('')
    setNewPassword('')
    setConfirmPassword('')
    setError('')
    setMessage('You have signed out. The in-memory token was cleared.')
    notify?.('Signed out')
  }

  async function changePassword(event) {
    event.preventDefault()

    if (busy || mode !== 'live') return

    if (newPassword !== confirmPassword) {
      setError('The new password and confirmation do not match.')
      return
    }

    if (!newPassword.trim()) {
      setError('Enter a new password.')
      return
    }

    setBusy(true)
    setError('')
    setMessage('')

    try {
      const result = await api.changePassword(currentPassword, newPassword)

      if (!result?.token) {
        clearAccessToken()
        setAccount(null)
        throw new Error(
          'Password changed, but the response contained no replacement token. Sign in again.',
        )
      }

      setAccessToken(result.token, result.expiresAt || null)
      setExpiresAt(result.expiresAt ? new Date(result.expiresAt).getTime() : null)
      setAccount({
        username: result.username || account?.username || '',
        roles: Array.isArray(result.roles)
          ? result.roles
          : account?.roles || [],
      })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setMessage('Password changed successfully. Your new session is active.')
      notify?.('Password changed')
    } catch (requestError) {
      setError(describeApiError(requestError))
    } finally {
      setBusy(false)
    }
  }

  const signedIn = Boolean(getAccessToken()) && Boolean(account)
  const expiryLabel = expiresAt
    ? new Date(expiresAt).toLocaleString()
    : 'Not provided by the server'

  return (
    <div className="account-layout">
      <section className="panel sign-in-panel">
        <div className="panel-heading">
          <div>
            <h2>{signedIn ? 'Account session' : 'Sign in to ReleasePilot'}</h2>
            <p>
              {mode === 'demo'
                ? 'Demo mode does not authenticate or perform release writes.'
                : 'Uses the existing Spring Boot authentication endpoints.'}
            </p>
          </div>
          {signedIn && (
            <span className="status-pill success">
              <span className="status-dot" />
              Signed in
            </span>
          )}
        </div>

        {mode === 'demo' && (
          <p className="form-note">
            Switch to Live mode to enable login. Demo mode does not store
            credentials and does not make authenticated requests.
          </p>
        )}

        {!signedIn && (
          <form className="sign-in-form" onSubmit={signIn}>
            <label>
              Username
              <input
                name="username"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                required
                placeholder="Your account username"
                disabled={mode === 'demo' || busy}
              />
            </label>

            <label>
              Password
              <input
                name="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                placeholder="Your password"
                disabled={mode === 'demo' || busy}
              />
            </label>

            <button
              className="button button-primary"
              disabled={mode === 'demo' || busy}
            >
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        )}

        {signedIn && (
          <>
            <dl className="account-details">
              <div>
                <dt>Username</dt>
                <dd>{account.username || 'Not supplied'}</dd>
              </div>
              <div>
                <dt>Roles</dt>
                <dd>{account.roles?.join(', ') || 'No roles returned'}</dd>
              </div>
              <div>
                <dt>Token expiry</dt>
                <dd>{expiryLabel}</dd>
              </div>
            </dl>

            <button
              className="button button-secondary"
              onClick={signOut}
              disabled={busy}
            >
              Sign out
            </button>
          </>
        )}
      </section>

      {signedIn && mode === 'live' && (
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Change password</h2>
              <p>
                Uses POST /api/auth/password. The backend returns a fresh
                token because the old session is invalidated.
              </p>
            </div>
          </div>

          <form className="sign-in-form" onSubmit={changePassword}>
            <label>
              Current password
              <input
                type="password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                required
                maxLength={128}
                disabled={busy}
              />
            </label>

            <label>
              New password
              <input
                type="password"
                autoComplete="new-password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                required
                maxLength={128}
                disabled={busy}
              />
            </label>

            <label>
              Confirm new password
              <input
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                required
                maxLength={128}
                disabled={busy}
              />
            </label>

            <button className="button button-primary" disabled={busy}>
              {busy ? 'Updating…' : 'Change password'}
            </button>
          </form>
        </section>
      )}

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Session safety</h2>
            <p>How this frontend handles authentication.</p>
          </div>
        </div>

        <ul className="security-list">
          <li>The JWT is held in memory, not local storage.</li>
          <li>A page reload clears the in-memory session.</li>
          <li>Expired tokens and API 401 responses clear the session.</li>
          <li>Spring Boot remains authoritative for permissions.</li>
          <li>Sign out clears the local token; it does not revoke a JWT server-side.</li>
        </ul>
      </section>

      {(error || message) && (
        <section className={`panel account-feedback ${error ? 'has-error' : ''}`}>
          {error && <p role="alert">{error}</p>}
          {message && <p role="status">{message}</p>}
        </section>
      )}
    </div>
  )
}
