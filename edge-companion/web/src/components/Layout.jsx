import { useNavigate, useLocation } from 'react-router-dom'

export default function Layout({ children }) {
  const nav = useNavigate()
  const loc = useLocation()
  const showBack = loc.pathname !== '/patients' && loc.pathname !== '/'

  return (
    <div className="app-shell">
      <header className="top-bar">
        {showBack && (
          <button type="button" className="btn-outlined" onClick={() => nav(-1)} aria-label="Back">
            ←
          </button>
        )}
        <h1>Med Veda</h1>
        <div className="brand-circle">M</div>
        <button type="button" className="btn-outlined" onClick={() => nav('/settings')} aria-label="Settings">
          ☰
        </button>
      </header>
      <main className="content">{children}</main>
    </div>
  )
}
