import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import { getViolationHistory, type PageResponse, type ViolationListItem } from './violationService'

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
})
