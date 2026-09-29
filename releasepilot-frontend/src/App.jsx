import { useState } from 'react'

export default function App() {
  const [message, setMessage] = useState('Hello, ReleasePilot.')
  const [reply, setReply] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function send() {
    setLoading(true)
    setError('')
    setReply('')
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
      })
      if (!res.ok) {
        throw new Error(`Backend returned status ${res.status}`)
      }
      const data = await res.json()
      setReply(data.reply)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 640, margin: '40px auto', fontFamily: 'sans-serif' }}>
      <h1>ReleasePilot</h1>
      <input
        style={{ width: '100%', padding: 10, boxSizing: 'border-box' }}
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        placeholder="Ask ReleasePilot..."
      />
      <button onClick={send} disabled={loading} style={{ marginTop: 10, padding: '8px 16px' }}>
        {loading ? 'Thinking...' : 'Send'}
      </button>

      {reply && (
        <p style={{ marginTop: 20, whiteSpace: 'pre-wrap' }}>
          <strong>ReleasePilot:</strong> {reply}
        </p>
      )}
      {error && <p style={{ marginTop: 20, color: 'crimson' }}>Error: {error}</p>}
    </div>
  )
}