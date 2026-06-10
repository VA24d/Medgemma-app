import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'

const empty = {
  name: '',
  dateOfBirth: '',
  gender: '',
  medicalRecordNumber: '',
  phoneNumber: '',
  email: '',
  address: '',
  bloodGroup: '',
  allergies: '',
  notes: '',
}

export default function PatientFormPage() {
  const { id } = useParams()
  const isEdit = Boolean(id)
  const nav = useNavigate()
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!isEdit) return
    api.patient(id).then((d) => setForm({ ...empty, ...d.patient }))
  }, [id, isEdit])

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }))

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      if (isEdit) {
        await api.updatePatient(id, form)
        nav(`/patients/${id}`)
      } else {
        const r = await api.createPatient(form)
        nav(`/patients/${r.patient.id}`)
      }
    } catch (err) {
      alert(err.message)
    }
    setSaving(false)
  }

  return (
    <>
      <h2>{isEdit ? 'Edit Patient' : 'New Patient'}</h2>
      <form onSubmit={submit}>
        <label>Name *</label>
        <input required value={form.name} onChange={(e) => set('name', e.target.value)} />
        <label>Date of birth</label>
        <input type="date" value={form.dateOfBirth} onChange={(e) => set('dateOfBirth', e.target.value)} />
        <label>Gender</label>
        <select value={form.gender} onChange={(e) => set('gender', e.target.value)}>
          <option value="">—</option>
          <option>Male</option>
          <option>Female</option>
          <option>Other</option>
        </select>
        <label>MRN</label>
        <input value={form.medicalRecordNumber} onChange={(e) => set('medicalRecordNumber', e.target.value)} />
        <label>Phone</label>
        <input value={form.phoneNumber} onChange={(e) => set('phoneNumber', e.target.value)} />
        <label>Email</label>
        <input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} />
        <label>Allergies</label>
        <input value={form.allergies} onChange={(e) => set('allergies', e.target.value)} />
        <label>Notes</label>
        <textarea rows={3} value={form.notes} onChange={(e) => set('notes', e.target.value)} />
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save'}
        </button>
      </form>
    </>
  )
}
