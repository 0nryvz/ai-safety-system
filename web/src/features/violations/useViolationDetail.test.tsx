import { cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as violationService from '../../services/violationService'
import type { ViolationDetailResponse } from '../../services/violationService'
import { useViolationDetail } from './useViolationDetail'

const detail: ViolationDetailResponse = {
  violationId: '11111111-1111-1111-1111-111111111111',
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
  version: 3,
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useViolationDetail', () => {
  it('loads violation detail', async () => {
    vi.spyOn(violationService, 'getViolationDetail').mockResolvedValue(detail)

    const { result } = renderHook(() => useViolationDetail(detail.violationId))

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toEqual(detail)
    expect(result.current.error).toBeNull()
  })

  it('exposes detail loading errors', async () => {
    const error = new Error('Detail request failed')

    vi.spyOn(violationService, 'getViolationDetail').mockRejectedValue(error)

    const { result } = renderHook(() => useViolationDetail(detail.violationId))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toBeNull()
    expect(result.current.error).toBe(error)
  })

  it('retries the detail request', async () => {
    const detailSpy = vi
      .spyOn(violationService, 'getViolationDetail')
      .mockRejectedValueOnce(new Error('Temporary failure'))
      .mockResolvedValueOnce(detail)

    const { result } = renderHook(() => useViolationDetail(detail.violationId))

    await waitFor(() => {
      expect(result.current.error).not.toBeNull()
    })

    result.current.retry()

    await waitFor(() => {
      expect(result.current.data).toEqual(detail)
    })

    expect(detailSpy).toHaveBeenCalledTimes(2)
  })
})
