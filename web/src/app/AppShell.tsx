import { LogOut } from 'lucide-react'
import type { ReactNode } from 'react'
import { performLogout } from '../features/auth/authActions'
import Button from '../shared/ui/Button/Button'
import './AppShell.css'

interface AppShellProps {
  children: ReactNode
}

function AppShell({ children }: AppShellProps) {
  async function handleLogout() {
    try {
      await performLogout()
    } catch {
      // performLogout local session'ı her durumda temizler.
    }
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>AI Safety System</h1>
          <p>Gerçek Zamanlı Güvenlik İzleme Paneli</p>
        </div>

        <Button
          type="button"
          variant="secondary"
          className="app-header__logout"
          onClick={handleLogout}
        >
          <LogOut size={18} aria-hidden="true" />
          Çıkış yap
        </Button>
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
