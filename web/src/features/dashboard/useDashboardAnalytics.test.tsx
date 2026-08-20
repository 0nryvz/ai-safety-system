import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../core/api/apiError'
import { getDashboardDistribution, getDashboardTrend } from '../../services/dashboardService'
import { useDashboardAnalytics } from './useDashboardAnalytics'

vi.mock('../../services/dashboardService', () => ({
  getDashboardTrend: vi.fn(),
  getDashboardDistribution: vi.fn(),
}))

interface AnalyticsHookProps {
  groupBy: 'TYPE' | 'CAMERA' | 'DEPARTMENT'
}

const mockedGetDashboardTrend = vi.mocked(getDashboardTrend)
const mockedGetDashboardDistribution = vi.mocked(getDashboardDistribution)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('useDashboardAnalytics', () => {
  it('loads trend and distribution together', async () => {
    mockedGetDashboardTrend.mockResolvedValue([
      {
        date: '2026-08-18',
        count: 4,
      },
    ])

    mockedGetDashboardDistribution.mockResolvedValue([
      {
        group: 'NO_HELMET',
        count: 3,
      },
    ])

    const { result } = renderHook(() =>
      useDashboardAnalytics({
        from: '2026-08-18',
        to: '2026-08-19',
        groupBy: 'TYPE',
      }),
    )

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.trend).toEqual([
      {
        date: '2026-08-18',
        count: 4,
      },
    ])

    expect(result.current.distribution).toEqual([
      {
        group: 'NO_HELMET',
        count: 3,
      },
    ])

    expect(result.current.error).toBeNull()

    expect(mockedGetDashboardTrend).toHaveBeenCalledWith({
      from: '2026-08-18',
      to: '2026-08-19',
    })

    expect(mockedGetDashboardDistribution).toHaveBeenCalledWith('TYPE')
  })

  it('reloads analytics when the groupBy value changes', async () => {
    mockedGetDashboardTrend.mockResolvedValue([])
    mockedGetDashboardDistribution.mockResolvedValue([])

    const { rerender } = renderHook(
      ({ groupBy }: AnalyticsHookProps) =>
        useDashboardAnalytics({
          from: '2026-08-18',
          to: '2026-08-19',
          groupBy,
        }),
      {
        initialProps: {
          groupBy: 'TYPE',
        } as AnalyticsHookProps,
      },
    )

    await waitFor(() => {
      expect(mockedGetDashboardDistribution).toHaveBeenCalledWith('TYPE')
    })

    rerender({
      groupBy: 'CAMERA',
    })

    await waitFor(() => {
      expect(mockedGetDashboardDistribution).toHaveBeenCalledWith('CAMERA')
    })

    expect(mockedGetDashboardDistribution).toHaveBeenCalledTimes(2)
    expect(mockedGetDashboardTrend).toHaveBeenCalledTimes(1)
  })

  it('preserves an API error', async () => {
    const error = new ApiError('Analytics failed', 500)

    mockedGetDashboardTrend.mockRejectedValue(error)
    mockedGetDashboardDistribution.mockResolvedValue([])

    const { result } = renderHook(() =>
      useDashboardAnalytics({
        from: '2026-08-18',
        to: '2026-08-19',
        groupBy: 'TYPE',
      }),
    )

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.error).toBe(error)
  })

  it('retries analytics after an error', async () => {
    mockedGetDashboardTrend
      .mockRejectedValueOnce(new ApiError('Network error', 0))
      .mockResolvedValueOnce([])

    mockedGetDashboardDistribution.mockResolvedValue([])

    const { result } = renderHook(() =>
      useDashboardAnalytics({
        from: '2026-08-18',
        to: '2026-08-19',
        groupBy: 'TYPE',
      }),
    )

    await waitFor(() => {
      expect(result.current.error?.status).toBe(0)
    })

    act(() => {
      result.current.retry()
    })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.error).toBeNull()
    })

    expect(mockedGetDashboardTrend).toHaveBeenCalledTimes(2)
    expect(mockedGetDashboardDistribution).toHaveBeenCalledTimes(2)
  })
})
