import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api'

export default function CloudPage() {
  const { patientId } = useParams()
  const isAll = patientId === '-1'
  const [job, setJob] = useState(null)
  const [force, setForce] = useState(false)
  const [started, setStarted] = useState(false)
  const [error, setError] = useState('')
  const [logs, setLogs] = useState([])

  const pollJob = async () => {
    try {
      setJob(await api.jobCurrent())
    } catch (_) {}
  }

  useEffect(() => {
    const run = async () => {
      try {
        if (isAll) await api.processAll(force)
        else await api.processPatient(Number(patientId), force)
        setStarted(true)
      } catch (e) {
        setError(e.message)
      }
    }
    run()
    const iv = setInterval(pollJob, 2000)
    const es = new EventSource('/v1/events')
    es.onmessage = (ev) => {
      try {
        const batch = JSON.parse(ev.data)
        for (const e of batch) {
          if (e.kind === 'heartbeat') continue
          setLogs((l) => [`[${e.kind}] ${e.message}`, ...l].slice(0, 40))
        }
      } catch (_) {}
    }
    return () => {
      clearInterval(iv)
      es.close()
    }
  }, [patientId, isAll, force])

  const pct = job?.total ? Math.round((job.current / job.total) * 100) : 0

  return (
    <>
      <h2>{isAll ? 'Cloud: All Patients' : 'Cloud Analysis'}</h2>
      <div className="card">
        <strong>Edge GPU (laptop)</strong>
        <p className="muted">MedGemma via Ollama — thinking off for batch enrichment</p>
      </div>
      <p>{job?.message || (started ? 'Running…' : 'Starting…')}</p>
      {job?.running && job.total > 0 && (
        <div className="progress-bar">
          <div style={{ width: `${pct}%` }} />
        </div>
      )}
      {error && <p style={{ color: 'var(--error)' }}>{error}</p>}
      <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <input type="checkbox" checked={force} onChange={(e) => setForce(e.target.checked)} />
        Force reprocess all entries
      </label>
      <div className="action-row">
        <button
          type="button"
          className="btn-primary"
          onClick={async () => {
            setError('')
            if (isAll) await api.processAll(force)
            else await api.processPatient(Number(patientId), force)
          }}
        >
          Run again
        </button>
        <button type="button" className="btn-outlined" onClick={() => api.cancelProcess()}>
          Cancel
        </button>
      </div>
      <h3>Live log</h3>
      <div className="log-box">
        {logs.map((l, i) => (
          <div key={i}>{l}</div>
        ))}
      </div>
    </>
  )
}
