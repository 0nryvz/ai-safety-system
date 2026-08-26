import { describe, expect, it } from 'vitest'
import {
  DEFAULT_VIOLATION_HISTORY_PAGE,
  DEFAULT_VIOLATION_HISTORY_PAGE_SIZE,
  parseViolationHistoryQuery,
  serializeViolationHistoryQuery,
} from './violationHistoryQuery'

describe('violationHistoryQuery', () => {
  it('parses supported URL query values', () => {
    const params = new URLSearchParams()

    params.set('from', '2026-08-18T00:00:00Z')
    params.set('to', '2026-08-18T23:59:59Z')
    params.set('type', 'MISSING_GLOVES')
    params.set('cameraId', 'camera-id')
    params.set('departmentId', 'department-id')
    params.set('lifecycleStatus', 'COMPLETED')
    params.set('reviewStatus', 'CONFIRMED')
    params.set('page', '2')
    params.set('size', '50')
    params.append('sort', 'startedAt,desc')
    params.append('sort', 'id,desc')

    expect(parseViolationHistoryQuery(params)).toEqual({
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
  })

  it('ignores unknown enum values', () => {
    const params = new URLSearchParams({
      type: 'UNKNOWN_TYPE',
      lifecycleStatus: 'UNKNOWN_STATUS',
      reviewStatus: 'UNKNOWN_REVIEW',
    })

    const result = parseViolationHistoryQuery(params)

    expect(result.type).toBeUndefined()
    expect(result.lifecycleStatus).toBeUndefined()
    expect(result.reviewStatus).toBeUndefined()
  })

  it('uses safe defaults for invalid pagination values', () => {
    const params = new URLSearchParams({
      page: '-10',
      size: '0',
    })

    const result = parseViolationHistoryQuery(params)

    expect(result.page).toBe(DEFAULT_VIOLATION_HISTORY_PAGE)

    expect(result.size).toBe(DEFAULT_VIOLATION_HISTORY_PAGE_SIZE)
  })

  it('serializes filters back into URL parameters', () => {
    const params = serializeViolationHistoryQuery({
      type: 'RESTRICTED_ZONE',
      lifecycleStatus: 'ACTIVE',
      reviewStatus: 'UNREVIEWED',
      cameraId: 'camera-id',
      page: 3,
      size: 50,
      sort: ['startedAt,desc'],
    })

    expect(params.get('type')).toBe('RESTRICTED_ZONE')
    expect(params.get('lifecycleStatus')).toBe('ACTIVE')
    expect(params.get('reviewStatus')).toBe('UNREVIEWED')
    expect(params.get('cameraId')).toBe('camera-id')
    expect(params.get('page')).toBe('3')
    expect(params.get('size')).toBe('50')
    expect(params.getAll('sort')).toEqual(['startedAt,desc'])
  })

  it('does not clutter the URL with default pagination values', () => {
    const params = serializeViolationHistoryQuery({
      page: DEFAULT_VIOLATION_HISTORY_PAGE,
      size: DEFAULT_VIOLATION_HISTORY_PAGE_SIZE,
    })

    expect(params.has('page')).toBe(false)
    expect(params.has('size')).toBe(false)
  })
})
