import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'

export default function NewEntryPage() {
  const { id: patientId } = useParams()
  const nav = useNavigate()
  const [entryType, setEntryType] = useState('MANUAL')
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await api.createEntry(patientId, { patientId: Number(patientId), entryType, title, content })
      nav(`/patients/${patientId}`)
    } catch (err) {
      alert(err.message)
    }
    setSaving(false)
  }

  return (
    <>
      <h2>New Entry</h2>
      <form onSubmit={submit}>
        <label>Type</label>
        <select value={entryType} onChange={(e) => setEntryType(e.target.value)}>
          <option value="MANUAL">Clinical note</option>
          <option value="DOCUMENT">Document</option>
          <option value="RECORDING">Recording / transcription</option>
          <option value="XRAY">X-ray</option>
          <option value="MRI">MRI</option>
          <option value="HISTOPATHOLOGY">Histopathology</option>
        </select>
        <label>Title</label>
        <input value={title} onChange={(e) => setTitle(e.target.value)} required />
        <label>Content</label>
        <textarea rows={6} value={content} onChange={(e) => setContent(e.target.value)} />
        <button type="submit" className="btn-primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save entry'}
        </button>
      </form>
    </>
  )
}
