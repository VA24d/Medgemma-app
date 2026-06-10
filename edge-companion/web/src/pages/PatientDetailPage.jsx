import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import { api, initials } from '../api'

export default function PatientDetailPage({ showDiagnosis = false }) {
  const { id } = useParams()
  const nav = useNavigate()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      setData(await api.patient(id))
    } catch (e) {
      setError(e.message)
    }
  }

  useEffect(() => {
    load()
  }, [id])

  if (error) return <p style={{ color: 'var(--error)' }}>{error}</p>
  if (!data) return <p className="muted">Loading…</p>

  const { patient: p, entries = [], diagnoses = [] } = data

  const handleDelete = async () => {
    if (!confirm(`Delete ${p.name}?`)) return
    await api.deletePatient(p.id)
    nav('/patients')
  }

  if (showDiagnosis) {
    return (
      <>
        <h2>Diagnosis — {p.name}</h2>
        {diagnoses.length === 0 && <p className="muted">No saved impressions yet.</p>}
        {diagnoses.map((d) => (
          <div key={d.id} className="card surface" style={{ marginBottom: 12 }}>
            <div className="muted">
              {new Date(d.generatedAt).toLocaleString()} · {d.scope} · {d.modelName}
            </div>
            <ReactMarkdown>{d.diagnosis}</ReactMarkdown>
          </div>
        ))}
        <button type="button" className="btn-outlined" onClick={() => nav(`/cloud/${p.id}`)}>
          Run cloud analysis
        </button>
      </>
    )
  }

  return (
    <>
      <div className="card">
        <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
          <div className="avatar" style={{ width: 56, height: 56, fontSize: '1.2rem' }}>
            {initials(p.name)}
          </div>
          <div>
            <h2 style={{ margin: '0 0 4px' }}>{p.name}</h2>
            <div className="muted">
              {[p.gender, p.dateOfBirth].filter(Boolean).join(' · ')}
              {p.medicalRecordNumber && ` · MRN ${p.medicalRecordNumber}`}
            </div>
            {p.allergies && (
              <div style={{ color: 'var(--error)', marginTop: 8 }}>⚠ Allergies: {p.allergies}</div>
            )}
          </div>
        </div>
      </div>

      <div className="action-row">
        <button type="button" className="btn-outlined" onClick={() => nav(`/patients/${p.id}/diagnosis`)}>
          Diagnosis
        </button>
        <button type="button" className="btn-outlined" onClick={() => nav(`/patients/${p.id}/edit`)}>
          Edit
        </button>
        <button type="button" className="btn-error" onClick={handleDelete}>
          Delete
        </button>
      </div>

      <button type="button" className="btn-tertiary" style={{ width: '100%', marginBottom: 8 }} onClick={() => nav(`/patients/${p.id}/chat`)}>
        Ask AI about this patient
      </button>
      <button type="button" className="btn-outlined" style={{ width: '100%', marginBottom: 16 }} onClick={() => nav(`/cloud/${p.id}`)}>
        Cloud analysis (GPU)
      </button>
      <button type="button" className="btn-primary" style={{ width: '100%', marginBottom: 16 }} onClick={() => nav(`/patients/${p.id}/entries/new`)}>
        New Entry
      </button>

      <h3>Recent Entries</h3>
      {entries.length === 0 && (
        <div className="card surface"><p className="muted">No entries yet.</p></div>
      )}
      {entries.slice().reverse().map((e) => (
        <div key={e.id} className="list-item">
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 500 }}>{e.title || e.entryType}</div>
            <div className="muted">{new Date(e.createdAt).toLocaleDateString()} · {e.entryType}</div>
            {(e.visitSummary || e.analysisResult) && (
              <p style={{ fontSize: '0.9rem', margin: '8px 0 0' }}>
                {(e.visitSummary || e.analysisResult).slice(0, 120)}…
              </p>
            )}
          </div>
          <span className={`badge ${e.status === 'reviewed' ? 'reviewed' : ''}`}>{e.status}</span>
        </div>
      ))}
    </>
  )
}
