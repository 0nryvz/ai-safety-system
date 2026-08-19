import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as violationService from '../../services/violationService'
import { useViolationClipUrl } from './useViolationClipUrl'

const violationId = '11111111-1111-1111-1111-111111111111'

const clipUrlResponse = {
  url: 'https://media.example.test/authorized-clip',
  expiresAt: '2026-08-18T10:05:00Z',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useViolationClipUrl', () => {
  it('does not request a clip URL before the recording is ready', () => {
    const getClipUrlSpy = vi.spyOn(violationService, 'getViolationClipUrl')

    const { result } = renderHook(() => useViolationClipUrl(violationId, 'PROCESSING'))

    expect(getClipUrlSpy).not.toHaveBeenCalled()
    expect(result.current.data).toBeNull()
    expect(result.current.isLoading).toBe(false)
  })

  it('loads the clip URL when the recording is ready', async () => {
    const getClipUrlSpy = vi
      .spyOn(violationService, 'getViolationClipUrl')
      .mockResolvedValue(clipUrlResponse)

    const { result } = renderHook(() => useViolationClipUrl(violationId, 'READY'))

    await waitFor(() => {
      expect(result.current.data).toEqual(clipUrlResponse)
    })

    expect(getClipUrlSpy).toHaveBeenCalledWith(violationId)
    expect(result.current.isLoading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('exposes an error and allows one controlled manual retry', async () => {
    const requestError = new Error('Request failed')
    const getClipUrlSpy = vi
      .spyOn(violationService, 'getViolationClipUrl')
      .mockRejectedValueOnce(requestError)
      .mockResolvedValueOnce(clipUrlResponse)

    const { result } = renderHook(() => useViolationClipUrl(violationId, 'READY'))

    await waitFor(() => {
      expect(result.current.error).toBe(requestError)
    })

    act(() => {
      result.current.retry()
    })

    await waitFor(() => {
      expect(result.current.data).toEqual(clipUrlResponse)
    })

    expect(getClipUrlSpy).toHaveBeenCalledTimes(2)
    expect(result.current.error).toBeNull()
  })

  it('refreshes the clip URL before it expires', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-08-18T10:00:00Z'))

    const setTimeoutSpy = vi.spyOn(window, 'setTimeout')
    const getClipUrlSpy = vi
      .spyOn(violationService, 'getViolationClipUrl')
      .mockResolvedValue(clipUrlResponse)

    const { result } = renderHook(() => useViolationClipUrl(violationId, 'READY'))

    await waitFor(() => {
      expect(result.current.data).toEqual(clipUrlResponse)
    })

    const refreshTimer = setTimeoutSpy.mock.calls.find(([, delay]) => delay === 270_000)
    const refreshCallback = refreshTimer?.[0] as (() => void) | undefined

    expect(refreshCallback).toBeDefined()

    act(() => {
      refreshCallback?.()
    })

    await waitFor(() => {
      expect(getClipUrlSpy).toHaveBeenCalledTimes(2)
    })
  })
})
