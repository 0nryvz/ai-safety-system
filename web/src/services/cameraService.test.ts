import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import {
  createCamera,
  getCamera,
  getCameras,
  getRestrictedZone,
  updateCamera,
  updateRestrictedZone,
  getReferenceImageUrl,
  uploadReferenceImage,
  type CameraResponse,
  type RestrictedZone,
  type ReferenceImageUrlResponse,
} from './cameraService'

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

  it('loads a camera by id', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: cameras[0],
    })

    const result = await getCamera(cameras[0].id)

    expect(getSpy).toHaveBeenCalledWith(`/cameras/${cameras[0].id}`)
    expect(result).toEqual(cameras[0])
  })

  it('creates a camera', async () => {
    const request = {
      name: 'Kamera 2',
      code: 'CAM-002',
      departmentId: '33333333-3333-3333-3333-333333333333',
    }

    const createdCamera: CameraResponse = {
      id: '44444444-4444-4444-4444-444444444444',
      name: request.name,
      code: request.code,
      departmentId: request.departmentId,
      departmentName: 'Montaj',
      active: true,
      connectionStatus: 'OFFLINE',
      lastSeenAt: null,
      activeSessionId: null,
    }

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: createdCamera,
    })

    const result = await createCamera(request)

    expect(postSpy).toHaveBeenCalledWith('/cameras', request)
    expect(result).toEqual(createdCamera)
  })

  it('updates a camera', async () => {
    const request = {
      name: 'Güncellenmiş Kamera',
      active: false,
    }

    const updatedCamera: CameraResponse = {
      ...cameras[0],
      name: request.name,
      active: false,
    }

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValue({
      data: updatedCamera,
    })

    const result = await updateCamera(cameras[0].id, request)

    expect(putSpy).toHaveBeenCalledWith(`/cameras/${cameras[0].id}`, request)

    expect(result).toEqual(updatedCamera)
  })

  it('loads a restricted zone by camera id', async () => {
    const restrictedZone: RestrictedZone = {
      name: 'Kaynak hattı',
      polygon: [
        { x: 0.1, y: 0.2 },
        { x: 0.8, y: 0.2 },
        { x: 0.8, y: 0.7 },
      ],
    }

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: restrictedZone,
    })

    const result = await getRestrictedZone(cameras[0].id)

    expect(getSpy).toHaveBeenCalledWith(`/cameras/${cameras[0].id}/restricted-zone`)
    expect(result).toEqual(restrictedZone)
  })

  it('updates a restricted zone for a camera', async () => {
    const restrictedZone: RestrictedZone = {
      name: 'Kaynak hattı',
      polygon: [
        { x: 0.1, y: 0.2 },
        { x: 0.8, y: 0.2 },
        { x: 0.8, y: 0.7 },
      ],
    }

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValue({
      data: undefined,
    })

    await updateRestrictedZone(cameras[0].id, restrictedZone)

    expect(putSpy).toHaveBeenCalledWith(`/cameras/${cameras[0].id}/restricted-zone`, restrictedZone)
  })

  it('loads a reference image URL by camera id', async () => {
    const referenceImage: ReferenceImageUrlResponse = {
      url: 'https://storage.example/reference-image.png',
      expiresAt: '2026-08-20T16:00:00Z',
    }

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: referenceImage,
    })

    const result = await getReferenceImageUrl(cameras[0].id)

    expect(getSpy).toHaveBeenCalledWith(`/cameras/${cameras[0].id}/reference-image-url`)
    expect(result).toEqual(referenceImage)
  })

  it('uploads a reference image as multipart form data', async () => {
    const file = new File(['reference-image'], 'reference.png', {
      type: 'image/png',
    })

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: undefined,
    })

    await uploadReferenceImage(cameras[0].id, file)

    expect(postSpy).toHaveBeenCalledWith(
      `/cameras/${cameras[0].id}/reference-image`,
      expect.any(FormData),
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      },
    )

    const formData = postSpy.mock.calls[0][1] as FormData
    expect(formData.get('file')).toBe(file)
  })
})
