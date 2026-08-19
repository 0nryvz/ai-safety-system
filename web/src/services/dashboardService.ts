import { apiClient } from '../core/api/apiClient'
import type {
  Camera,
  DashboardSummary,
  RecentViolation,
} from '../features/dashboard/dashboardTypes'

export async function getDashboardSummary(): Promise<DashboardSummary> {
  const response = await apiClient.get<DashboardSummary>('/dashboard/summary')

  return response.data
}

export async function getRecentViolations(): Promise<RecentViolation[]> {
  const response = await apiClient.get<RecentViolation[]>('/dashboard/recent-violations')

  return response.data
}

export async function getCameras(): Promise<Camera[]> {
  const response = await apiClient.get<Camera[]>('/cameras')

  return response.data
}
