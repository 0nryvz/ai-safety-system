import { apiClient } from '../core/api/apiClient'
import type {
  Camera,
  DashboardDistributionGroup,
  DashboardDistributionItem,
  DashboardSummary,
  DashboardTrendParams,
  DashboardTrendPoint,
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

export async function getDashboardTrend({
  from,
  to,
  bucket = 'DAY',
}: DashboardTrendParams): Promise<DashboardTrendPoint[]> {
  const response = await apiClient.get<DashboardTrendPoint[]>('/dashboard/trend', {
    params: {
      from,
      to,
      bucket,
    },
  })

  return response.data
}

export async function getDashboardDistribution(
  groupBy: DashboardDistributionGroup,
): Promise<DashboardDistributionItem[]> {
  const response = await apiClient.get<DashboardDistributionItem[]>('/dashboard/distribution', {
    params: {
      groupBy,
    },
  })

  return response.data
}
