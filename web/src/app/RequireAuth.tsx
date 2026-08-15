import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthSession } from '../features/auth/useAuthSession'
import { ROUTE_PATHS } from './routeConfig'

function RequireAuth() {
  const location = useLocation()
  const { status } = useAuthSession()

  if (status !== 'authenticated') {
    return <Navigate to={ROUTE_PATHS.login} replace state={{ from: location }} />
  }

  return <Outlet />
}

export default RequireAuth
