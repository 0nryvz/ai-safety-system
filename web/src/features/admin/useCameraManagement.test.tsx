import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as cameraService from '../../services/cameraService'
import * as realtimeRuntime from '../../core/realtime/realtimeRuntime'
import { REALTIME_REST_REFRESH_DEBOUNCE_MS } from '../../core/realtime/useRealtimeRestRefresh'
import { useCameraManagement } from './useCameraManagement'

const cameras = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Kamera 1',
    code: 'CAM-001',
    departmentId: '22222222-2222-2222-2222-222222222222',
    departmentName: 'Kaynak',
    active: true,
    status: 'ONLINE',
    lastSeenAt: '2026-08-19T10:00:00Z',
    activeSessionId: null,
  },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useCameraManagement', () => {
  it('loads cameras', async () => {
    vi.spyOn(cameraService, 'getCameras').mockResolvedValue(cameras)

    const { result } = renderHook(() => useCameraManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toEqual(cameras)
    expect(result.current.error).toBeNull()
  })

  it('exposes loading errors', async () => {
    vi.spyOn(cameraService, 'getCameras').mockRejectedValue(new Error('camera load failed'))

    const { result } = renderHook(() => useCameraManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toEqual([])
    expect(result.current.error).toBeInstanceOf(Error)
  })

  it('retries the camera request', async () => {
    const getCamerasSpy = vi
      .spyOn(cameraService, 'getCameras')
      .mockRejectedValueOnce(new Error('camera load failed'))
      .mockResolvedValueOnce(cameras)

    const { result } = renderHook(() => useCameraManagement())

    await waitFor(() => {
      expect(result.current.error).toBeInstanceOf(Error)
    })

    result.current.retry()

    await waitFor(() => {
      expect(result.current.data).toEqual(cameras)
    })

    expect(getCamerasSpy).toHaveBeenCalledTimes(2)
  })

  it('refreshes cameras after realtime recovery without clearing the list on failure', async () => {
    vi.useFakeTimers()

    let recoveryListener: (() => void) | undefined

    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeRecovery').mockImplementation((listener) => {
      recoveryListener = listener
      return vi.fn()
    })
    vi.spyOn(realtimeRuntime, 'subscribeToRealtimeMessages').mockReturnValue(vi.fn())

    const refreshedCameras = [
      {
        ...cameras[0],
        status: 'OFFLINE',
      },
    ]

    const getCamerasSpy = vi
      .spyOn(cameraService, 'getCameras')
      .mockResolvedValueOnce(cameras)
      .mockResolvedValueOnce(refreshedCameras)

    const { result } = renderHook(() => useCameraManagement())

    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.data).toEqual(cameras)

    await act(async () => {
      recoveryListener?.()
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
      await Promise.resolve()
    })

    expect(getCamerasSpy).toHaveBeenCalledTimes(2)
    expect(result.current.data).toEqual(refreshedCameras)
    expect(result.current.isLoading).toBe(false)

    getCamerasSpy.mockRejectedValueOnce(new Error('recovery refresh failed'))

    await act(async () => {
      recoveryListener?.()
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
      await Promise.resolve()
    })

    expect(result.current.data).toEqual(refreshedCameras)
    expect(result.current.error).toBeNull()

    vi.useRealTimers()
  })

  it('does not refresh cameras on violation realtime messages', async () => {
    const getCamerasSpy = vi.spyOn(cameraService, 'getCameras').mockResolvedValue(cameras)
    const subscribeMessagesSpy = vi.spyOn(realtimeRuntime, 'subscribeToRealtimeMessages')

    renderHook(() => useCameraManagement())

    await waitFor(() => {
      expect(getCamerasSpy).toHaveBeenCalledTimes(1)
    })

    expect(subscribeMessagesSpy).not.toHaveBeenCalled()
  })
})
