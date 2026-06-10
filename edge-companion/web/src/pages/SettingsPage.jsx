import { useEffect, useState } from 'react'
import { api } from '../api'

const THEMES = [
  { id: 'purple', label: 'MedGemma Purple' },
  { id: 'white', label: 'Light Medical' },
  { id: 'black', label: 'Dark AMOLED' },
]

export default function SettingsPage() {
  const [health, setHealth] = useState(null)
  const [settings, setSettings] = useState(null)
  const [theme, setTheme] = useState(localStorage.getItem('medveda-theme') || 'purple')
  const [syncSince, setSyncSince] = useState(0)

  const load = async () => {
    setHealth(await api.health())
    setSettings(await api.settings())
  }

  useEffect(() => {
    load()
    const iv = setInterval(load, 10000)
    return () => clearInterval(iv)
  }, [])

  const applyTheme = (t) => {
    setTheme(t)
    localStorage.setItem('medveda-theme', t)
    document.documentElement.setAttribute('data-theme', t)
  }

  const saveNight = async (patch) => {
    const s = await api.updateSettings(patch)
    setSettings(s)
  }

  const syncNow = async () => {
    const r = await api.syncPull(syncSince)
    setSyncSince(r.cursor)
    alert(`Pulled ${r.patients?.length || 0} patients, ${r.entries?.length || 0} entries`)
    load()
  }

  return (
    <>
      <h2>Settings</h2>

      <div className="drawer-section">Connection</div>
      <div className="card surface">
        {health && (
          <>
            <span className={`pill ${health.ollama_ok ? 'ok' : 'bad'}`}>
              {health.ollama_ok ? 'Ollama OK' : 'Ollama DOWN'}
            </span>
            <p className="muted" style={{ marginTop: 8 }}>
              Model: {health.default_model}
            </p>
            <p className="muted">Wi-Fi URL: {health.phone_url_wifi}</p>
            <p className="muted">USB URL: {health.phone_url_usb}</p>
          </>
        )}
      </div>

      <div className="drawer-section">Appearance</div>
      {THEMES.map((t) => (
        <div
          key={t.id}
          className="drawer-item"
          style={{ background: theme === t.id ? 'var(--primary-container)' : undefined }}
          onClick={() => applyTheme(t.id)}
          role="button"
          tabIndex={0}
        >
          {t.label}
        </div>
      ))}

      <div className="drawer-section">Edge Cloud — Night batch</div>
      {settings && (
        <div className="card surface">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <strong>Nightly GPU processing</strong>
            <span className={`pill ${settings.night_batch_enabled !== false ? 'ok' : 'bad'}`}>
              {settings.night_batch_enabled !== false ? 'On' : 'Off'}
            </span>
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
            <input
              type="checkbox"
              checked={settings.night_batch_enabled !== false}
              onChange={(e) => saveNight({ night_batch_enabled: e.target.checked })}
            />
            Enable automatic night processing
          </label>
          <p className="muted" style={{ marginBottom: 12, lineHeight: 1.5 }}>
            Runs only while your <strong>laptop is on</strong>, plugged in (optional), and{' '}
            <strong>edge companion is running</strong> (<code>start.ps1</code>). If you start the
            laptop during the night window, it catches up once per night.
          </p>
          {settings.nightBatchStatus && (
            <p className="muted" style={{ marginBottom: 12 }}>{settings.nightBatchStatus}</p>
          )}
          {settings.night_batch_enabled !== false && (
            <>
              <label>Window start hour (0–23)</label>
              <input
                type="number"
                min={0}
                max={23}
                value={settings.night_start_hour ?? 0}
                onChange={(e) => saveNight({ night_start_hour: Number(e.target.value) })}
              />
              <label>Window end hour (0–23)</label>
              <input
                type="number"
                min={0}
                max={23}
                value={settings.night_end_hour ?? 2}
                onChange={(e) => saveNight({ night_end_hour: Number(e.target.value) })}
              />
              <p className="muted">
                {settings.nightBatchInWindow ? 'Inside night window now.' : 'Outside night window.'}
              </p>
              <p className="muted">Next scheduled run: {settings.nextNightRun || '—'}</p>
            </>
          )}
          <p className="muted">
            Last night batch:{' '}
            {settings.lastNightBatch
              ? new Date(Number(settings.lastNightBatch)).toLocaleString()
              : 'Never'}
          </p>
        </div>
      )}

      <div className="drawer-section">Sync</div>
      <button type="button" className="btn-primary" onClick={syncNow}>
        Sync now (pull from laptop DB)
      </button>
      {settings && (
        <p className="muted" style={{ marginTop: 8 }}>
          Sync cursor: {settings.cursor}
        </p>
      )}
    </>
  )
}
