import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getViolationHistory } from '../../services/violationService'
import { loadRealtimeRecoverySnapshots } from './realtimeRecoveryLoader'

vi.mock('../../services/violationService', async () => {
  const actual = await vi.importActual<typeof import('../../services/violationService')>(
    '../../services/violationService',
  )

  return {
    ...actual,
    getViolationHistory: vi.fn(),
  }
})

const mockedGetViolationHistory = vi.mocked(getViolationHistory)

describe('loadRealtimeRecoverySnapshots', () => {
  beforeEach(() => {
    mockedGetViolationHistory.mockReset()
  })

  it('maps authoritative violation list items to recovery snapshots', async () => {
    mockedGetViolationHistory.mockResolvedValue({
      content: [
        {
          violationId: 'violation-1',
          cameraId: 'camera-1',
          departmentId: 'department-1',
          type: 'MISSING_GLOVES',
          startedAt: '2026-08-20T10:00:00Z',
          endedAt: null,
          confidence: 0.94,
          lifecycleStatus: 'ACTIVE',
          reviewStatus: 'UNREVIEWED',
          recordingStatus: 'PROCESSING',
          updatedAt: '2026-08-20T10:05:00Z',
        },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    })

    await expect(loadRealtimeRecoverySnapshots()).resolves.toEqual([
      {
        violationId: 'violation-1',
        lifecycleStatus: 'ACTIVE',
        recordingStatus: 'PROCESSING',
        updatedAt: '2026-08-20T10:05:00Z',
      },
    ])

    expect(mockedGetViolationHistory).toHaveBeenCalledWith({
      lifecycleStatus: 'ACTIVE',
      page: 0,
      size: 100,
      sort: ['updatedAt,desc'],
    })
  })

  it('skips violations that do not have a recording status', async () => {
    mockedGetViolationHistory.mockResolvedValue({
      content: [
        {
          violationId: 'violation-1',
          cameraId: 'camera-1',
          departmentId: 'department-1',
          type: 'MISSING_GLOVES',
          startedAt: '2026-08-20T10:00:00Z',
          endedAt: null,
          confidence: 0.94,
          lifecycleStatus: 'ACTIVE',
          reviewStatus: 'UNREVIEWED',
          recordingStatus: null,
          updatedAt: '2026-08-20T10:05:00Z',
        },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    })

    await expect(loadRealtimeRecoverySnapshots()).resolves.toEqual([])
  })
})
