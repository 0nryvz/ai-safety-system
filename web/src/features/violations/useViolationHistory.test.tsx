import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as violationService from '../../services/violationService'
import type { PageResponse, ViolationListItem } from '../../services/violationService'
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
})
