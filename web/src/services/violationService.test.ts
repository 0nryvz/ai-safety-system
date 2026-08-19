import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import {
  getViolationClipUrl,
  getViolationHistory,
  getViolationDetail,
  reviewViolation,
  type ViolationDetailResponse,
  type PageResponse,
  type ViolationListItem,
} from './violationService'

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
  vi.restoreAllMocks()
})

describe('violationService', () => {
  it('loads paginated violation history', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: response,
    })

    const result = await getViolationHistory()

    expect(getSpy).toHaveBeenCalledTimes(1)
    expect(getSpy.mock.calls[0][0]).toBe('/violations')
    expect(result).toEqual(response)
  })

  it('maps supported filters to backend query parameters', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: response,
    })

    await getViolationHistory({
      from: '2026-08-18T00:00:00Z',
      to: '2026-08-18T23:59:59Z',
      type: 'MISSING_GLOVES',
      cameraId: 'camera-id',
      departmentId: 'department-id',
      lifecycleStatus: 'COMPLETED',
      reviewStatus: 'CONFIRMED',
      page: 2,
      size: 50,
      sort: ['startedAt,desc', 'id,desc'],
    })

    const config = getSpy.mock.calls[0][1]
    const params = config?.params as URLSearchParams

    expect(params.get('from')).toBe('2026-08-18T00:00:00Z')
    expect(params.get('to')).toBe('2026-08-18T23:59:59Z')
    expect(params.get('type')).toBe('MISSING_GLOVES')
    expect(params.get('cameraId')).toBe('camera-id')
    expect(params.get('departmentId')).toBe('department-id')
    expect(params.get('lifecycleStatus')).toBe('COMPLETED')
    expect(params.get('reviewStatus')).toBe('CONFIRMED')
    expect(params.get('page')).toBe('2')
    expect(params.get('size')).toBe('50')
    expect(params.getAll('sort')).toEqual(['startedAt,desc', 'id,desc'])
  })

  it('does not send unsupported filters', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: response,
    })

    await getViolationHistory()

    const config = getSpy.mock.calls[0][1]
    const params = config?.params as URLSearchParams

    expect(params.has('recordingStatus')).toBe(false)
  })

  it('loads an authorized clip URL without sending an object key', async () => {
    const violationId = '11111111-1111-1111-1111-111111111111'
    const clipUrlResponse = {
      url: 'https://media.example.test/authorized-clip',
      expiresAt: '2026-08-18T10:05:00Z',
    }
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: clipUrlResponse,
    })

    const result = await getViolationClipUrl(violationId)

    expect(getSpy).toHaveBeenCalledWith(`/violations/${violationId}/clip-url`)
    expect(getSpy.mock.calls[0]).toHaveLength(1)
    expect(result).toEqual(clipUrlResponse)
    expect(result).not.toHaveProperty('objectKey')
  })

  it('loads violation detail from the confirmed endpoint', async () => {
    const violationId = '11111111-1111-1111-1111-111111111111'

    const detail: ViolationDetailResponse = {
      violationId,
      cameraId: '22222222-2222-2222-2222-222222222222',
      cameraName: 'Kaynak Kamera 1',
      cameraCode: 'CAM-001',
      departmentId: '33333333-3333-3333-3333-333333333333',
      departmentName: 'Kaynak',
      sessionId: '44444444-4444-4444-4444-444444444444',
      type: 'MISSING_GLOVES',
      confidence: 0.94,
      modelVersion: 'model-v1',
      detectedAt: '2026-08-19T10:00:00Z',
      startedAt: '2026-08-19T10:00:00Z',
      endedAt: null,
      lifecycleStatus: 'ACTIVE',
      reviewStatus: 'UNREVIEWED',
      reviewedBy: null,
      reviewedAt: null,
      recordingStatus: 'PROCESSING',
      clipReady: false,
      playbackUrl: null,
      coverImageKey: null,
      coverImageReady: false,
    }

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: detail,
    })

    const result = await getViolationDetail(violationId)

    expect(getSpy).toHaveBeenCalledWith(`/violations/${violationId}`)
    expect(result).toEqual(detail)
  })

  it('submits a supported violation review status', async () => {
    const violationId = '11111111-1111-1111-1111-111111111111'

    const reviewResponse = {
      violationId,
      reviewStatus: 'CONFIRMED' as const,
      reviewedBy: '55555555-5555-5555-5555-555555555555',
      reviewedAt: '2026-08-19T10:05:00Z',
    }

    const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValue({
      data: reviewResponse,
    })

    const result = await reviewViolation(violationId, 'CONFIRMED')

    expect(patchSpy).toHaveBeenCalledWith(`/violations/${violationId}/review`, {
      reviewStatus: 'CONFIRMED',
    })

    expect(result).toEqual(reviewResponse)
  })
})
