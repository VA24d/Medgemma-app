import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import { api } from '../api'

export default function ChatPage() {
  const { id } = useParams()
  const patientId = id ? Number(id) : null
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [patientName, setPatientName] = useState('')
  const bottomRef = useRef(null)

  useEffect(() => {
    if (patientId) {
      api.patient(patientId).then((d) => {
        setPatientName(d.patient.name)
        setMessages([
          {
            role: 'ai',
            text: `📋 Patient loaded: **${d.patient.name}** (${d.entries?.length || 0} entries). Ask me anything about this patient.`,
          },
        ])
      })
    } else {
      setMessages([{ role: 'ai', text: 'AI Assistant ready. Ask a general medical question.' }])
    }
  }, [patientId])

  const send = async () => {
    const text = input.trim()
    if (!text || loading) return
    setInput('')
    setMessages((m) => [{ role: 'user', text }, ...m])
    setLoading(true)
    try {
      const r = await api.chat(text, patientId)
      setMessages((m) => [{ role: 'ai', text: r.reply }, ...m])
    } catch (e) {
      setMessages((m) => [{ role: 'ai', text: `Error: ${e.message}` }, ...m])
    }
    setLoading(false)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 'calc(100vh - 80px)' }}>
      <h2 style={{ margin: '0 0 8px' }}>
        {patientId ? `Patient: ${patientName}` : 'AI Assistant'}
      </h2>
      {loading && <p className="muted">Generating…</p>}
      <div className="chat-messages">
        {messages.map((m, i) => (
          <div key={i} className={`bubble ${m.role}`}>
            <ReactMarkdown>{m.text}</ReactMarkdown>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <div className="chat-input-row">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && send()}
          placeholder="Ask a question…"
          disabled={loading}
        />
        <button type="button" className="btn-primary" onClick={send} disabled={loading}>
          Send
        </button>
      </div>
    </div>
  )
}
