import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import RequireAuth from './app/RequireAuth'
import { ROUTE_PATHS } from './app/routeConfig'
import { useAuthSession } from './features/auth/useAuthSession'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import ViolationHistoryPage from './pages/ViolationHistoryPage'
import ViolationDetailPage from './pages/ViolationDetailPage'
import RequireRole from './app/RequireRole'
import CameraManagementPage from './pages/CameraManagementPage'
import UserManagementPage from './pages/UserManagementPage'
import RestrictedZoneEditorPage from './pages/RestrictedZoneEditorPage'

interface LoginLocationState {
  from?: {
    pathname: string
  }
}

function LoginRoute() {
  const navigate = useNavigate()
  const location = useLocation()
  const { status, session } = useAuthSession()
  const state = location.state as LoginLocationState | null

  if (status === 'authenticated' && session?.user) {
    return <Navigate to={ROUTE_PATHS.dashboard} replace />
  }

  function handleLoginSuccess() {
    navigate(state?.from?.pathname ?? ROUTE_PATHS.dashboard, {
      replace: true,
    })
  }

  return <LoginPage onLoginSuccess={handleLoginSuccess} />
}

function HomeRoute() {
  const { status } = useAuthSession()

  return (
    <Navigate to={status === 'authenticated' ? ROUTE_PATHS.dashboard : ROUTE_PATHS.login} replace />
  )
}

function App() {
  return (
    <Routes>
      <Route path={ROUTE_PATHS.home} element={<HomeRoute />} />
      <Route path={ROUTE_PATHS.login} element={<LoginRoute />} />

      <Route element={<RequireAuth />}>
        <Route path={ROUTE_PATHS.dashboard} element={<DashboardPage />} />
        <Route path={ROUTE_PATHS.violations} element={<ViolationHistoryPage />} />
        <Route path={ROUTE_PATHS.violationDetail} element={<ViolationDetailPage />} />
      </Route>

      <Route element={<RequireRole access="admin" />}>
        <Route path={ROUTE_PATHS.adminCameras} element={<CameraManagementPage />} />
        <Route path={ROUTE_PATHS.restrictedZoneEditor} element={<RestrictedZoneEditorPage />} />
        <Route path={ROUTE_PATHS.adminUsers} element={<UserManagementPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
