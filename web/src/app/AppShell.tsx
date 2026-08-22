import { LogOut } from 'lucide-react'
import type { ReactNode } from 'react'
import { performLogout } from '../features/auth/authActions'
import Button from '../shared/ui/Button/Button'
import './AppShell.css'
import { NavLink } from 'react-router-dom'
import { ROUTE_PATHS } from './routeConfig'
import AlertCenter from '../features/alerts/AlertCenter'
import { useAuthSession } from '../features/auth/useAuthSession'
import { hasRouteAccess } from '../features/auth/roleAccess'

interface AppShellProps {
  children: ReactNode
}

function AppShell({ children }: AppShellProps) {
  const { session } = useAuthSession()

  const canAccessAdmin = hasRouteAccess('admin', session?.user?.roles ?? [])
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

        <div className="app-header__actions">
          <AlertCenter />

          <Button
            type="button"
            variant="secondary"
            className="app-header__logout"
            onClick={handleLogout}
          >
            <LogOut size={18} aria-hidden="true" />
            Çıkış yap
          </Button>
        </div>
      </header>

      <div className="app-layout">
        <aside className="app-sidebar" aria-label="Ana menü">
          <nav>
            <nav>
              <NavLink to={ROUTE_PATHS.dashboard}>Dashboard</NavLink>

              {canAccessAdmin && <NavLink to={ROUTE_PATHS.adminCameras}>Kameralar</NavLink>}

              <NavLink to={ROUTE_PATHS.violations}>İhlaller</NavLink>

              {canAccessAdmin && <NavLink to={ROUTE_PATHS.adminUsers}>Kullanıcılar</NavLink>}

              {canAccessAdmin && <NavLink to={ROUTE_PATHS.adminDepartments}>Departmanlar</NavLink>}
            </nav>
          </nav>
        </aside>

        <main className="app-content">{children}</main>
      </div>
    </div>
  )
}

export default AppShell
