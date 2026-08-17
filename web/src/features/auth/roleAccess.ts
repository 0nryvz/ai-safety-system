import type { RouteAccess } from '../../app/routeConfig'
import type { UserRole } from './sessionTypes'

export const ROUTE_ACCESS_ROLES: Record<RouteAccess, readonly UserRole[]> = {
  public: [],
  authenticated: ['ADMIN', 'OHS_SPECIALIST', 'SHIFT_SUPERVISOR'],
  admin: ['ADMIN'],
}

export function hasRouteAccess(access: RouteAccess, roles: UserRole[]): boolean {
  if (access === 'public') {
    return true
  }

  const allowedRoles = ROUTE_ACCESS_ROLES[access]

  return roles.some((role) => allowedRoles.includes(role))
}
