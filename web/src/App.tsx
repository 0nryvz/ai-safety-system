import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import RequireAuth from './app/RequireAuth'
import { ROUTE_PATHS } from './app/routeConfig'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'

interface LoginLocationState {
  from?: {
    pathname: string
  }
}

function LoginRoute() {
  const navigate = useNavigate()
  const location = useLocation()
  const isAuthenticated = Boolean(sessionStorage.getItem('accessToken'))
  const state = location.state as LoginLocationState | null

  if (isAuthenticated) {
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
  const isAuthenticated = Boolean(sessionStorage.getItem('accessToken'))

  return <Navigate to={isAuthenticated ? ROUTE_PATHS.dashboard : ROUTE_PATHS.login} replace />
}

function App() {
  return (
    <Routes>
      <Route path={ROUTE_PATHS.home} element={<HomeRoute />} />
      <Route path={ROUTE_PATHS.login} element={<LoginRoute />} />

      <Route element={<RequireAuth />}>
        <Route path={ROUTE_PATHS.dashboard} element={<DashboardPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
