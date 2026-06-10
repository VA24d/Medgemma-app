import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, initials } from '../api'

export default function PatientsPage() {
  const [patients, setPatients] = useState([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const nav = useNavigate()

  const load = async (q = '') => {
    setLoading(true)
    try {
      const data = await api.patients(q)
      setPatients(data.patients || [])
    } catch (e) {
      console.error(e)
    }
    setLoading(false)
  }

  useEffect(() => {
    load()
  }, [])

  return (
    <>
      <input
        placeholder="Search patients…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && load(search)}
      />
      <div className="action-row">
        <button type="button" className="btn-primary" onClick={() => nav('/patients/new')}>
          New Patient
        </button>
        <button type="button" className="btn-tertiary" onClick={() => nav('/chat')}>
          Open Chat
        </button>
      </div>
      <button type="button" className="btn-outlined" style={{ width: '100%', marginBottom: 16 }} onClick={() => nav('/cloud/-1')}>
        Process all charts on cloud GPU
      </button>

      {loading && <p className="muted">Loading…</p>}
      {!loading && patients.length === 0 && (
        <div className="card surface">
          <p>No patients yet. Add one on the phone or here — they sync both ways.</p>
        </div>
      )}
      {patients.map((p) => (
        <div key={p.id} className="list-item" onClick={() => nav(`/patients/${p.id}`)} role="button" tabIndex={0}>
          <div className="avatar">{initials(p.name)}</div>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 500 }}>{p.name}</div>
            <div className="muted">
              {[p.gender, p.dateOfBirth].filter(Boolean).join(' · ')}
              {p.medicalRecordNumber && ` · MRN ${p.medicalRecordNumber}`}
            </div>
          </div>
          <span>›</span>
        </div>
      ))}
    </>
  )
}
