import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ROUTE_PATHS } from './routeConfig'

function RequireAuth() {
  const location = useLocation()
  const isAuthenticated = Boolean(sessionStorage.getItem('accessToken'))

  if (!isAuthenticated) {
    return <Navigate to={ROUTE_PATHS.login} replace state={{ from: location }} />
  }

  return <Outlet />
}

export default RequireAuth
