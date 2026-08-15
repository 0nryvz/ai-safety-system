import { Navigate, Outlet } from 'react-router-dom'
import { useAuthSession } from '../features/auth/useAuthSession'
import { hasRouteAccess } from '../features/auth/roleAccess'
import type { RouteAccess } from './routeConfig'
import { ROUTE_PATHS } from './routeConfig'

interface RequireRoleProps {
  access: Exclude<RouteAccess, 'public'>
}

function RequireRole({ access }: RequireRoleProps) {
  const { status, session } = useAuthSession()

  if (status !== 'authenticated') {
    return <Navigate to={ROUTE_PATHS.login} replace />
  }

  const roles = session?.user?.roles ?? []

  if (!hasRouteAccess(access, roles)) {
    return <Navigate to={ROUTE_PATHS.dashboard} replace />
  }

  return <Outlet />
}

export default RequireRole
