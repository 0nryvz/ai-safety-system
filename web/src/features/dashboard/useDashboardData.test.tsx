import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../core/api/apiError'
import {
  getCameras,
  getDashboardSummary,
  getRecentViolations,
} from '../../services/dashboardService'
import type { DashboardSummary } from './dashboardTypes'
import { useDashboardData } from './useDashboardData'
import { subscribeToRealtimeRecovery } from '../../core/realtime/realtimeRuntime'

vi.mock('../../services/dashboardService', () => ({
  getDashboardSummary: vi.fn(),
  getRecentViolations: vi.fn(),
  getCameras: vi.fn(),
}))

vi.mock('../../core/realtime/realtimeRuntime', () => ({
  subscribeToRealtimeRecovery: vi.fn(),
}))

const summary: DashboardSummary = {
  todayViolationCount: 4,
  last7DaysViolationCount: 18,
  mostFrequentViolationType: 'NO_HELMET',
  activeCameraCount: 6,
  offlineCameraCount: 2,
  activeViolationCount: 3,
}

const mockedGetDashboardSummary = vi.mocked(getDashboardSummary)
const mockedGetRecentViolations = vi.mocked(getRecentViolations)
const mockedGetCameras = vi.mocked(getCameras)
const mockedSubscribeToRealtimeRecovery = vi.mocked(subscribeToRealtimeRecovery)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('useDashboardData', () => {
  it('loads summary, recent violations and cameras together', async () => {
    mockedGetDashboardSummary.mockResolvedValue(summary)
    mockedGetRecentViolations.mockResolvedValue([])
    mockedGetCameras.mockResolvedValue([])

    const { result } = renderHook(() => useDashboardData({ includeSummary: true }))

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.summary).toEqual(summary)
    expect(result.current.recentViolations).toEqual([])
    expect(result.current.cameras).toEqual([])
    expect(result.current.error).toBeNull()

    expect(mockedGetDashboardSummary).toHaveBeenCalledTimes(1)
    expect(mockedGetRecentViolations).toHaveBeenCalledTimes(1)
    expect(mockedGetCameras).toHaveBeenCalledTimes(1)
  })

  it('preserves an API error for the dashboard error policy', async () => {
    const forbiddenError = new ApiError('Forbidden', 403)

    mockedGetDashboardSummary.mockRejectedValue(forbiddenError)
    mockedGetRecentViolations.mockResolvedValue([])
    mockedGetCameras.mockResolvedValue([])

    const { result } = renderHook(() => useDashboardData({ includeSummary: true }))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.error).toBe(forbiddenError)
    expect(result.current.summary).toBeNull()
  })

  it('retries the complete dashboard request after an error', async () => {
    mockedGetDashboardSummary
      .mockRejectedValueOnce(new ApiError('Network error', 0))
      .mockResolvedValueOnce(summary)
    mockedGetRecentViolations.mockResolvedValue([])
    mockedGetCameras.mockResolvedValue([])

    const { result } = renderHook(() => useDashboardData({ includeSummary: true }))

    await waitFor(() => {
      expect(result.current.error?.status).toBe(0)
    })

    act(() => {
      result.current.retry()
    })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.error).toBeNull()

    await waitFor(() => {
      expect(result.current.summary).toEqual(summary)
    })

    expect(result.current.isLoading).toBe(false)
    expect(mockedGetDashboardSummary).toHaveBeenCalledTimes(2)
    expect(mockedGetRecentViolations).toHaveBeenCalledTimes(2)
    expect(mockedGetCameras).toHaveBeenCalledTimes(2)
  })
  it('skips the global summary request when summary access is disabled', async () => {
    mockedGetRecentViolations.mockResolvedValue([])
    mockedGetCameras.mockResolvedValue([])

    const { result } = renderHook(() => useDashboardData({ includeSummary: false }))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(mockedGetDashboardSummary).not.toHaveBeenCalled()
    expect(mockedGetRecentViolations).toHaveBeenCalledTimes(1)
    expect(mockedGetCameras).toHaveBeenCalledTimes(1)
    expect(result.current.summary).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('reloads dashboard REST state after realtime recovery', async () => {
    let recoveryListener: (() => void | Promise<void>) | null = null

    mockedSubscribeToRealtimeRecovery.mockImplementation((listener) => {
      recoveryListener = listener
      return vi.fn()
    })

    mockedGetDashboardSummary.mockResolvedValue(summary)
    mockedGetRecentViolations.mockResolvedValue([])
    mockedGetCameras.mockResolvedValue([])

    renderHook(() => useDashboardData({ includeSummary: true }))

    await waitFor(() => {
      expect(mockedGetDashboardSummary).toHaveBeenCalledTimes(1)
    })

    await act(async () => {
      await recoveryListener?.()
    })

    await waitFor(() => {
      expect(mockedGetDashboardSummary).toHaveBeenCalledTimes(2)
      expect(mockedGetRecentViolations).toHaveBeenCalledTimes(2)
      expect(mockedGetCameras).toHaveBeenCalledTimes(2)
    })
  })
})
