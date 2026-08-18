import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import { getCameras, type CameraResponse } from './cameraService'

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
  },
]

afterEach(() => {
  vi.restoreAllMocks()
})

describe('cameraService', () => {
  it('loads cameras', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: cameras,
    })

    const result = await getCameras()

    expect(getSpy).toHaveBeenCalledWith('/cameras')
    expect(result).toEqual(cameras)
  })
})
