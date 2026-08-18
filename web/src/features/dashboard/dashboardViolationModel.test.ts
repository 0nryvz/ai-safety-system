import { describe, expect, it } from 'vitest'
import type { RealtimeViolationRecord } from '../../core/realtime/realtimeViolationReducer'
import type { RecentViolation } from './dashboardTypes'
import { mergeDashboardViolations } from './dashboardViolationModel'

const recentViolation: RecentViolation = {
  violationId: 'violation-1',
  detectedAt: '2026-08-18T12:00:00Z',
  startedAt: '2026-08-18T12:01:00Z',
  violationType: 'MISSING_GLOVES',
  cameraId: 'camera-1',
  departmentId: 'department-1',
  cameraName: 'Kamera 1',
  cameraCode: 'CAM-001',
  lifecycleStatus: 'ACTIVE',
  reviewStatus: null,
  recordingStatus: 'REQUESTED',
  recordingReadyAt: null,
  recordingObjectKey: null,
  coverImageKey: null,
  confidence: 0.88,
  modelVersion: null,
}

const realtimeViolation: RealtimeViolationRecord = {
  violationId: 'violation-1',
  type: 'MISSING_GLOVES',
  cameraName: 'Kamera 1',
  departmentName: 'Montaj',
  startedAt: '2026-08-18T12:01:00Z',
  confidence: 0.94,
  lifecycleStatus: 'COMPLETED',
  recordingStatus: 'READY',
  clipReady: true,
  coverImageReady: true,
  lastEventAt: '2026-08-18T12:05:00Z',
  dismissed: false,
  errorCode: null,
}

describe('mergeDashboardViolations', () => {
  it('keeps different REST and realtime violations', () => {
    const result = mergeDashboardViolations(
      [recentViolation],
      [
        {
          ...realtimeViolation,
          violationId: 'violation-2',
        },
      ],
    )

    expect(result).toHaveLength(2)
    expect(result.map((violation) => violation.violationId)).toEqual(['violation-2', 'violation-1'])
  })

  it('uses the realtime record when the same violation exists in both sources', () => {
    const result = mergeDashboardViolations([recentViolation], [realtimeViolation])

    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({
      violationId: 'violation-1',
      departmentName: 'Montaj',
      lifecycleStatus: 'COMPLETED',
      recordingStatus: 'READY',
      confidence: 0.94,
      source: 'REALTIME',
    })
  })

  it('removes a dismissed realtime violation from the merged dashboard list', () => {
    const result = mergeDashboardViolations(
      [recentViolation],
      [
        {
          ...realtimeViolation,
          dismissed: true,
        },
      ],
    )

    expect(result).toEqual([])
  })

  it('keeps missing REST department names empty instead of guessing them', () => {
    const result = mergeDashboardViolations([recentViolation], [])

    expect(result[0]).toMatchObject({
      departmentName: null,
      source: 'REST',
    })
  })

  it('uses detectedAt when a REST violation has no startedAt value', () => {
    const result = mergeDashboardViolations(
      [
        {
          ...recentViolation,
          startedAt: null,
        },
      ],
      [],
    )

    expect(result[0].occurredAt).toBe('2026-08-18T12:00:00Z')
  })
})
