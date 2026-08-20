import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import type {
  Camera,
  DashboardDistributionItem,
  DashboardSummary,
  DashboardTrendPoint,
  RecentViolation,
} from '../features/dashboard/dashboardTypes'
import {
  getCameras,
  getDashboardDistribution,
  getDashboardSummary,
  getDashboardTrend,
  getRecentViolations,
} from './dashboardService'

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

  it('loads dashboard trend using the confirmed date range contract', async () => {
    const trend: DashboardTrendPoint[] = [
      {
        date: '2026-08-18',
        count: 4,
      },
      {
        date: '2026-08-19',
        count: 7,
      },
    ]

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: trend,
    })

    const result = await getDashboardTrend({
      from: '2026-08-18',
      to: '2026-08-19',
    })

    expect(getSpy).toHaveBeenCalledWith('/dashboard/trend', {
      params: {
        from: '2026-08-18',
        to: '2026-08-19',
        bucket: 'DAY',
      },
    })

    expect(result).toEqual(trend)
  })

  it('loads dashboard distribution using the confirmed groupBy contract', async () => {
    const distribution: DashboardDistributionItem[] = [
      {
        group: 'NO_HELMET',
        count: 8,
      },
      {
        group: 'RESTRICTED_ZONE',
        count: 3,
      },
    ]

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: distribution,
    })

    const result = await getDashboardDistribution('TYPE')

    expect(getSpy).toHaveBeenCalledWith('/dashboard/distribution', {
      params: {
        groupBy: 'TYPE',
      },
    })

    expect(result).toEqual(distribution)
  })
})
