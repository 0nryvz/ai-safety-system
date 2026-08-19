import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import type {
  Camera,
  DashboardSummary,
  RecentViolation,
} from '../features/dashboard/dashboardTypes'
import { getCameras, getDashboardSummary, getRecentViolations } from './dashboardService'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('dashboardService', () => {
  it('loads the dashboard summary from the confirmed endpoint', async () => {
    const summary: DashboardSummary = {
      todayViolationCount: 4,
      last7DaysViolationCount: 18,
      mostFrequentViolationType: 'NO_HELMET',
      activeCameraCount: 6,
      offlineCameraCount: 2,
      activeViolationCount: 3,
    }

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: summary,
    })

    const result = await getDashboardSummary()

    expect(getSpy).toHaveBeenCalledWith('/dashboard/summary')
    expect(result).toEqual(summary)
  })

  it('loads recent violations from the confirmed endpoint', async () => {
    const violations: RecentViolation[] = []

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: violations,
    })

    const result = await getRecentViolations()

    expect(getSpy).toHaveBeenCalledWith('/dashboard/recent-violations')
    expect(result).toEqual(violations)
  })

  it('loads cameras from the confirmed endpoint', async () => {
    const cameras: Camera[] = []

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: cameras,
    })

    const result = await getCameras()

    expect(getSpy).toHaveBeenCalledWith('/cameras')
    expect(result).toEqual(cameras)
  })
})
