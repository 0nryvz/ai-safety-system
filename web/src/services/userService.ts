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

export interface DepartmentResponse {
  id: string
  name: string
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

export async function getMyDepartments(): Promise<DepartmentResponse[]> {
  const response = await apiClient.get<DepartmentResponse[]>('/users/me/departments')

  return response.data
}

export interface CreateUserRequest {
  email: string
  password: string
  fullName: string
  departmentIds: string[]
  roleNames: UserRole[]
}

export interface UpdateUserRequest {
  fullName: string
  departmentIds: string[]
  roleNames: UserRole[]
  active?: boolean
}

export async function getUsers(): Promise<UserResponse[]> {
  const response = await apiClient.get<UserResponse[]>('/users')

  return response.data
}

export async function getUser(userId: string): Promise<UserResponse> {
  const response = await apiClient.get<UserResponse>(`/users/${userId}`)

  return response.data
}

export async function createUser(request: CreateUserRequest): Promise<UserResponse> {
  const response = await apiClient.post<UserResponse>('/users', request)

  return response.data
}

export async function updateUser(
  userId: string,
  request: UpdateUserRequest,
): Promise<UserResponse> {
  const response = await apiClient.patch<UserResponse>(`/users/${userId}`, request)

  return response.data
}

export async function deactivateUser(userId: string): Promise<void> {
  await apiClient.delete(`/users/${userId}`)
}
