export const ROUTE_PATHS = {
  home: '/',
  login: '/login',
  dashboard: '/dashboard',
  violations: '/violations',
  violationDetail: '/violations/:id',
  adminCameras: '/admin/cameras',
  restrictedZoneEditor: '/admin/cameras/:cameraId/restricted-zone',
  adminUsers: '/admin/users',
  adminDepartments: '/admin/departments',
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
    id: 'violationDetail',
    path: ROUTE_PATHS.violationDetail,
    owner: 'FE2',
    access: 'authenticated',
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
    id: 'violations',
    path: ROUTE_PATHS.violations,
    owner: 'FE2',
    access: 'authenticated',
  },
  {
    id: 'adminCameras',
    path: ROUTE_PATHS.adminCameras,
    owner: 'FE2',
    access: 'admin',
  },
  {
    id: 'restrictedZoneEditor',
    path: ROUTE_PATHS.restrictedZoneEditor,
    owner: 'FE2',
    access: 'admin',
  },
  {
    id: 'adminUsers',
    path: ROUTE_PATHS.adminUsers,
    owner: 'FE2',
    access: 'admin',
  },
  {
    id: 'adminDepartments',
    path: ROUTE_PATHS.adminDepartments,
    owner: 'FE2',
    access: 'admin',
  },

  {
    id: 'notFound',
    path: '*',
    owner: 'FE1',
    access: 'public',
  },
]
