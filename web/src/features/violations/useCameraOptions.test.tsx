import { cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as cameraService from '../../services/cameraService'
import type { CameraResponse } from '../../services/cameraService'
import { useCameraOptions } from './useCameraOptions'

const cameras: CameraResponse[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Kamera 1',
    code: 'CAM-001',
    departmentId: '22222222-2222-2222-2222-222222222222',
    active: true,
    connectionStatus: 'ONLINE',
    lastSeenAt: '2026-08-18T18:00:00Z',
    activeSessionId: null,
    departmentName: 'Kaynak',
  },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useCameraOptions', () => {
  it('loads camera options', async () => {
    vi.spyOn(cameraService, 'getCameras').mockResolvedValue(cameras)

    const { result } = renderHook(() => useCameraOptions())

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.cameras).toEqual(cameras)
    expect(result.current.error).toBeNull()
  })

  it('exposes camera loading errors', async () => {
    const error = new Error('Camera request failed')

    vi.spyOn(cameraService, 'getCameras').mockRejectedValue(error)

    const { result } = renderHook(() => useCameraOptions())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.cameras).toEqual([])
    expect(result.current.error).toBe(error)
  })
})
