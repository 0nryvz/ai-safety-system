import { apiClient } from '../core/api/apiClient'

export interface CameraResponse {
  id: string
  name: string
  code: string
  departmentId: string
  active: boolean
  connectionStatus: string
  lastSeenAt: string | null
  activeSessionId: string | null
}

export async function getCameras(): Promise<CameraResponse[]> {
  const response = await apiClient.get<CameraResponse[]>('/cameras')

  return response.data
}
