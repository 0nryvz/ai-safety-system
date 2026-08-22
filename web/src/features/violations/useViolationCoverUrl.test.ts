import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as violationService from '../../services/violationService'
import { useViolationCoverUrl } from './useViolationCoverUrl'

const violationId = '11111111-1111-1111-1111-111111111111'

const coverUrlResponse = {
  url: 'https://media.example.test/authorized-cover',
  expiresAt: '2026-08-18T10:05:00Z',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useViolationCoverUrl', () => {
  it('does not request a cover URL before the cover is ready', () => {
    const getCoverUrlSpy = vi.spyOn(violationService, 'getViolationCoverUrl')

    const { result } = renderHook(() => useViolationCoverUrl(violationId, false))

    expect(getCoverUrlSpy).not.toHaveBeenCalled()
    expect(result.current.data).toBeNull()
    expect(result.current.isLoading).toBe(false)
  })

  it('loads the cover URL when the cover is ready', async () => {
    const getCoverUrlSpy = vi
      .spyOn(violationService, 'getViolationCoverUrl')
      .mockResolvedValue(coverUrlResponse)

    const { result } = renderHook(() => useViolationCoverUrl(violationId, true))

    await waitFor(() => {
      expect(result.current.data).toEqual(coverUrlResponse)
    })

    expect(getCoverUrlSpy).toHaveBeenCalledWith(violationId)
    expect(result.current.isLoading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('exposes an error and allows one controlled manual retry', async () => {
    const requestError = new Error('Request failed')
    const getCoverUrlSpy = vi
      .spyOn(violationService, 'getViolationCoverUrl')
      .mockRejectedValueOnce(requestError)
      .mockResolvedValueOnce(coverUrlResponse)

    const { result } = renderHook(() => useViolationCoverUrl(violationId, true))

    await waitFor(() => {
      expect(result.current.error).toBe(requestError)
    })

    act(() => {
      result.current.retry()
    })

    await waitFor(() => {
      expect(result.current.data).toEqual(coverUrlResponse)
    })

    expect(getCoverUrlSpy).toHaveBeenCalledTimes(2)
    expect(result.current.error).toBeNull()
  })

  it('refreshes the cover URL before it expires', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-08-18T10:00:00Z'))

    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')
    const getCoverUrlSpy = vi
      .spyOn(violationService, 'getViolationCoverUrl')
      .mockResolvedValue(coverUrlResponse)

    const { result } = renderHook(() => useViolationCoverUrl(violationId, true))

    await waitFor(() => {
      expect(result.current.data).toEqual(coverUrlResponse)
    })

    const refreshTimer = setTimeoutSpy.mock.calls.find(([, delay]) => delay === 270_000)
    const refreshCallback = refreshTimer?.[0] as (() => void) | undefined

    expect(refreshCallback).toBeDefined()

    act(() => {
      refreshCallback?.()
    })

    await waitFor(() => {
      expect(getCoverUrlSpy).toHaveBeenCalledTimes(2)
    })
  })
})