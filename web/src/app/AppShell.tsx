import type { ReactNode } from 'react'
import './AppShell.css'

interface AppShellProps {
  children: ReactNode
}

function AppShell({ children }: AppShellProps) {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>AI Safety System</h1>
          <p>Gerçek Zamanlı Güvenlik İzleme Paneli</p>
        </div>
      </header>

      <div className="app-layout">
        <aside className="app-sidebar" aria-label="Ana menü">
          <nav>
            <span>Dashboard</span>
            <span>Kameralar</span>
            <span>İhlaller</span>
            <span>Kullanıcılar</span>
          </nav>
        </aside>

        <main className="app-content">{children}</main>
      </div>
    </div>
  )
}

export default AppShell