import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as violationService from '../../services/violationService'
import type { PageResponse, ViolationListItem } from '../../services/violationService'
import * as realtimeRuntime from '../../core/realtime/realtimeRuntime'
import { REALTIME_REST_REFRESH_DEBOUNCE_MS } from '../../core/realtime/useRealtimeRestRefresh'
import { useViolationHistory } from './useViolationHistory'

const response: PageResponse<ViolationListItem> = {
  content: [
    {
      violationId: '11111111-1111-1111-1111-111111111111',
      cameraId: '22222222-2222-2222-2222-222222222222',
      departmentId: '33333333-3333-3333-3333-333333333333',
      type: 'MISSING_GLOVES',
      startedAt: '2026-08-18T10:00:00Z',
      endedAt: null,
      confidence: 0.94,
      lifecycleStatus: 'ACTIVE',
      reviewStatus: 'UNREVIEWED',
      recordingStatus: 'REQUESTED',
      updatedAt: '2026-08-18T10:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useViolationHistory', () => {
  it('loads violation history', async () => {
    vi.spyOn(violationService, 'getViolationHistory').mockResolvedValue(response)

    const { result } = renderHook(() =>
      useViolationHistory({
        page: 0,
        size: 20,
      }),
    )

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toEqual(response)
    expect(result.current.error).toBeNull()
  })

  it('reloads when the query changes', async () => {
    const getHistorySpy = vi
      .spyOn(violationService, 'getViolationHistory')
      .mockResolvedValue(response)

    const { rerender } = renderHook(
      ({ page }) =>
        useViolationHistory({
          page,
          size: 20,
        }),
      {
        initialProps: {
          page: 0,
        },
      },
    )

    await waitFor(() => {
      expect(getHistorySpy).toHaveBeenCalledTimes(1)
    })

    rerender({
      page: 1,
    })

    await waitFor(() => {
      expect(getHistorySpy).toHaveBeenCalledTimes(2)
    })

    expect(getHistorySpy).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
    })
  })

  it('exposes request errors', async () => {
    const error = new Error('Request failed')

    vi.spyOn(violationService, 'getViolationHistory').mockRejectedValue(error)

    const { result } = renderHook(() =>
      useViolationHistory({
        page: 0,
        size: 20,
      }),
    )

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toBeNull()
    expect(result.current.error).toBe(error)
  })

  it('retries the current query', async () => {
    const getHistorySpy = vi
      .spyOn(violationService, 'getViolationHistory')
      .mockRejectedValueOnce(new Error('Temporary failure'))
      .mockResolvedValueOnce(response)

    const { result } = renderHook(() =>
      useViolationHistory({
        page: 0,
        size: 20,
      }),
    )

    await waitFor(() => {
      expect(result.current.error).not.toBeNull()
    })

    act(() => {
      result.current.retry()
    })

    await waitFor(() => {
      expect(result.current.data).toEqual(response)
    })

    expect(getHistorySpy).toHaveBeenCalledTimes(2)
    expect(result.current.error).toBeNull()
  })

  it('refetches the current query after a realtime violation event', async () => {
    vi.useFakeTimers()

    let messageListener: (() => void) | undefined

    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeMessages').mockImplementation((listener) => {
      messageListener = () => listener({ body: '', headers: {} })
      return vi.fn()
    })
    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeRecovery').mockReturnValue(vi.fn())

    const refreshedResponse: PageResponse<ViolationListItem> = {
      ...response,
      totalElements: 2,
    }

    const getHistorySpy = vi
      .spyOn(violationService, 'getViolationHistory')
      .mockResolvedValueOnce(response)
      .mockResolvedValueOnce(refreshedResponse)

    const { result } = renderHook(() =>
      useViolationHistory({
        page: 0,
        size: 20,
        type: 'MISSING_GLOVES',
      }),
    )

    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.data).toEqual(response)

    await act(async () => {
      messageListener?.()
      messageListener?.()
      messageListener?.()
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
      await Promise.resolve()
    })

    expect(getHistorySpy).toHaveBeenCalledTimes(2)
    expect(getHistorySpy).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      type: 'MISSING_GLOVES',
    })
    expect(result.current.data).toEqual(refreshedResponse)
    expect(result.current.isLoading).toBe(false)

    vi.useRealTimers()
  })

  it('refetches the current query after realtime recovery', async () => {
    vi.useFakeTimers()

    let recoveryListener: (() => void) | undefined

    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeMessages').mockReturnValue(vi.fn())
    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeRecovery').mockImplementation((listener) => {
      recoveryListener = listener
      return vi.fn()
    })

    const getHistorySpy = vi
      .spyOn(violationService, 'getViolationHistory')
      .mockResolvedValueOnce(response)
      .mockResolvedValueOnce({
        ...response,
        totalElements: 3,
      })

    const { result } = renderHook(() =>
      useViolationHistory({
        page: 1,
        size: 20,
      }),
    )

    await act(async () => {
      await Promise.resolve()
    })

    await act(async () => {
      recoveryListener?.()
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
      await Promise.resolve()
    })

    expect(getHistorySpy).toHaveBeenCalledTimes(2)
    expect(getHistorySpy).toHaveBeenLastCalledWith({
      page: 1,
      size: 20,
    })
    expect(result.current.data?.totalElements).toBe(3)

    vi.useRealTimers()
  })

  it('keeps the current history list when a realtime refresh fails', async () => {
    vi.useFakeTimers()

    let messageListener: (() => void) | undefined

    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeMessages').mockImplementation((listener) => {
      messageListener = () => listener({ body: '', headers: {} })
      return vi.fn()
    })
    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeRecovery').mockReturnValue(vi.fn())

    vi.spyOn(violationService, 'getViolationHistory')
      .mockResolvedValueOnce(response)
      .mockRejectedValueOnce(new Error('refresh failed'))

    const { result } = renderHook(() =>
      useViolationHistory({
        page: 0,
        size: 20,
      }),
    )

    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.data).toEqual(response)

    await act(async () => {
      messageListener?.()
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
      await Promise.resolve()
    })

    expect(result.current.data).toEqual(response)
    expect(result.current.error).toBeNull()
    expect(result.current.isLoading).toBe(false)

    vi.useRealTimers()
  })

  it('keeps the loaded history while no realtime refresh occurs', async () => {
    vi.spyOn(violationService, 'getViolationHistory').mockResolvedValue(response)

    const { result } = renderHook(() =>
      useViolationHistory({
        page: 0,
        size: 20,
      }),
    )

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toEqual(response)
    expect(result.current.error).toBeNull()
  })
})
