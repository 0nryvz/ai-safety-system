export const ROUTE_PATHS = {
  home: '/',
  login: '/login',
  dashboard: '/dashboard',
} as const

export type RouteOwner = 'FE1' | 'FE2'
export type RouteAccess = 'public' | 'authenticated' | 'admin'

export interface AppRouteConfig {
  id: string
  path: string
  owner: RouteOwner
  access: RouteAccess
}

export const appRouteConfig: AppRouteConfig[] = [
  {
    id: 'home',
    path: ROUTE_PATHS.home,
    owner: 'FE1',
    access: 'public',
  },
  {
    id: 'login',
    path: ROUTE_PATHS.login,
    owner: 'FE2',
    access: 'public',
  },
  {
    id: 'dashboard',
    path: ROUTE_PATHS.dashboard,
    owner: 'FE1',
    access: 'authenticated',
  },
  {
    id: 'notFound',
    path: '*',
    owner: 'FE1',
    access: 'public',
  },
]
