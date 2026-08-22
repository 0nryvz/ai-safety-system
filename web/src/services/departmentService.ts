import { apiClient } from '../core/api/apiClient'

export interface DepartmentResponse {
  id: string
  code: string
  name: string
  description: string | null
  active: boolean
}

export interface CreateDepartmentRequest {
  code: string
  name: string
  description?: string
}

export interface UpdateDepartmentRequest {
  name?: string
  description?: string
  active?: boolean
}

export async function getDepartments(): Promise<DepartmentResponse[]> {
  const response = await apiClient.get<DepartmentResponse[]>('/departments')

  return response.data
}

export async function createDepartment(
  request: CreateDepartmentRequest,
): Promise<DepartmentResponse> {
  const response = await apiClient.post<DepartmentResponse>(
    '/departments',
    request,
  )

  return response.data
}

export async function updateDepartment(
  departmentId: string,
  request: UpdateDepartmentRequest,
): Promise<DepartmentResponse> {
  const response = await apiClient.patch<DepartmentResponse>(
    `/departments/${departmentId}`,
    request,
  )

  return response.data
}