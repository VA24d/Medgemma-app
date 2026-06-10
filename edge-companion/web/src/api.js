const BASE = ''

async function req(path, opts = {}) {
  const r = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...opts.headers },
    ...opts,
  })
  if (!r.ok) {
    const t = await r.text()
    throw new Error(t || `HTTP ${r.status}`)
  }
  return r.json()
}

export const api = {
  health: () => req('/health'),
  patients: (q = '') => req(`/v1/patients${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  patient: (id) => req(`/v1/patients/${id}`),
  createPatient: (body) => req('/v1/patients', { method: 'POST', body: JSON.stringify(body) }),
  updatePatient: (id, body) => req(`/v1/patients/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deletePatient: (id) => req(`/v1/patients/${id}`, { method: 'DELETE' }),
  createEntry: (patientId, body) =>
    req(`/v1/patients/${patientId}/entries`, { method: 'POST', body: JSON.stringify(body) }),
  deleteEntry: (id) => req(`/v1/entries/${id}`, { method: 'DELETE' }),
  chat: (message, patientId = null) =>
    req('/v1/chat', {
      method: 'POST',
      body: JSON.stringify({ message, patient_id: patientId }),
    }),
  processPatient: (id, force = false) =>
    req(`/v1/process/patient/${id}?force=${force}`, { method: 'POST' }),
  processAll: (force = false) => req(`/v1/process/all?force=${force}`, { method: 'POST' }),
  cancelProcess: () => req('/v1/process/cancel', { method: 'POST' }),
  jobCurrent: () => req('/v1/process/jobs/current'),
  settings: () => req('/v1/settings'),
  updateSettings: (body) => req('/v1/settings', { method: 'PUT', body: JSON.stringify(body) }),
  syncPull: (since = 0) => req(`/v1/sync/pull?since=${since}&device_id=web`),
}

export function initials(name) {
  return (name || '?')
    .split(' ')
    .map((w) => w[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
}
