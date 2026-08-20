import { apiClient } from '../core/api/apiClient'

export interface CameraResponse {
  id: string
  name: string
  code: string
  departmentId: string
  departmentName: string | null
  active: boolean
  connectionStatus: string
  lastSeenAt: string | null
  activeSessionId: string | null
}

export interface CreateCameraRequest {
  name: string
  code: string
  departmentId: string
}

export interface UpdateCameraRequest {
  name?: string
  code?: string
  departmentId?: string
  active?: boolean
}

export interface RestrictedZonePoint {
  x: number
  y: number
}

export interface RestrictedZone {
  name: string
  polygon: RestrictedZonePoint[]
}

export async function getCameras(): Promise<CameraResponse[]> {
  const response = await apiClient.get<CameraResponse[]>('/cameras')

  return response.data
}

export async function getCamera(cameraId: string): Promise<CameraResponse> {
  const response = await apiClient.get<CameraResponse>(`/cameras/${cameraId}`)

  return response.data
}

export async function createCamera(request: CreateCameraRequest): Promise<CameraResponse> {
  const response = await apiClient.post<CameraResponse>('/cameras', request)

  return response.data
}

export async function updateCamera(
  cameraId: string,
  request: UpdateCameraRequest,
): Promise<CameraResponse> {
  const response = await apiClient.put<CameraResponse>(`/cameras/${cameraId}`, request)

  return response.data
}

export async function getRestrictedZone(cameraId: string): Promise<RestrictedZone> {
  const response = await apiClient.get<RestrictedZone>(`/cameras/${cameraId}/restricted-zone`)

  return response.data
}

export async function updateRestrictedZone(
  cameraId: string,
  restrictedZone: RestrictedZone,
): Promise<void> {
  await apiClient.put(`/cameras/${cameraId}/restricted-zone`, restrictedZone)
}
