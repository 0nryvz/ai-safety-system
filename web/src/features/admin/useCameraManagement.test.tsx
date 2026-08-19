import { cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as cameraService from '../../services/cameraService'
import { useCameraManagement } from './useCameraManagement'

const cameras = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Kamera 1',
    code: 'CAM-001',
    departmentId: '22222222-2222-2222-2222-222222222222',
    departmentName: 'Kaynak',
    active: true,
    connectionStatus: 'ONLINE',
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
})
