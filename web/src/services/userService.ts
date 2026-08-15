import { apiClient } from '../core/api/apiClient'
import type { AuthUser, UserRole } from '../features/auth/sessionTypes'

export interface UserResponse {
  id: string
  email: string
  fullName: string
  active: boolean
  departmentId: string | null
  departmentName: string | null
  roles: string[]
  departmentIds: string[]
  createdAt: string
}

function isUserRole(role: string): role is UserRole {
  return role === 'ADMIN' || role === 'OHS_SPECIALIST' || role === 'SHIFT_SUPERVISOR'
}

export function mapUserResponseToAuthUser(response: UserResponse): AuthUser {
  return {
    id: response.id,
    email: response.email,
    fullName: response.fullName,
    active: response.active,
    roles: response.roles.filter(isUserRole),
    departmentIds: response.departmentIds,
  }
}

export async function getCurrentUser(): Promise<UserResponse> {
  const response = await apiClient.get<UserResponse>('/users/me')

  return response.data
}
