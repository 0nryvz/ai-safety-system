import { describe, expect, it } from 'vitest'
import {
  reconcileRealtimeViolations,
  type RealtimeRecoverySnapshot,
} from './realtimeRecoveryReconciler'
import type { RealtimeViolationRecord, RealtimeViolationState } from './realtimeViolationReducer'

const violation: RealtimeViolationRecord = {
  eventId: 'violation-1',
  version: 1,
  violationId: 'violation-1',
  type: 'MISSING_GLOVES',
  cameraName: 'Kamera 1',
  departmentName: 'Kaynak',
  startedAt: '2026-08-20T10:00:00Z',
  confidence: 0.94,
  lifecycleStatus: 'ACTIVE',
  recordingStatus: 'REQUESTED',
  clipReady: false,
  coverImageReady: false,
  lastEventAt: '2026-08-20T10:00:00Z',
  dismissed: false,
  errorCode: null,
}

function createState(): RealtimeViolationState {
  return {
    byId: {
      [violation.violationId]: violation,
    },
  }
}

describe('reconcileRealtimeViolations', () => {
  it('applies a newer authoritative REST snapshot', () => {
    const state = createState()

    const snapshots: RealtimeRecoverySnapshot[] = [
      {
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        updatedAt: '2026-08-20T10:05:00Z',
      },
    ]

    const result = reconcileRealtimeViolations(state, snapshots)

    expect(result.byId['violation-1']).toMatchObject({
      lifecycleStatus: 'COMPLETED',
      recordingStatus: 'READY',
      lastEventAt: '2026-08-20T10:05:00Z',
    })
  })

  it('keeps realtime state when the REST snapshot is older', () => {
    const state = createState()

    const result = reconcileRealtimeViolations(state, [
      {
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        updatedAt: '2026-08-20T09:59:00Z',
      },
    ])

    expect(result).toBe(state)
  })

  it('keeps realtime state when timestamps are equal', () => {
    const state = createState()

    const result = reconcileRealtimeViolations(state, [
      {
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        updatedAt: violation.lastEventAt,
      },
    ])

    expect(result).toBe(state)
  })

  it('does not create a realtime record for an unknown REST violation', () => {
    const state = createState()

    const result = reconcileRealtimeViolations(state, [
      {
        violationId: 'violation-from-rest-only',
        lifecycleStatus: 'ACTIVE',
        recordingStatus: 'PROCESSING',
        updatedAt: '2026-08-20T10:05:00Z',
      },
    ])

    expect(result).toBe(state)
    expect(result.byId['violation-from-rest-only']).toBeUndefined()
  })

  it('ignores a snapshot with an invalid updatedAt value', () => {
    const state = createState()

    const result = reconcileRealtimeViolations(state, [
      {
        violationId: 'violation-1',
        lifecycleStatus: 'ERROR',
        recordingStatus: 'ERROR',
        updatedAt: 'invalid-date',
      },
    ])

    expect(result).toBe(state)
  })

  it('preserves realtime-only fields when applying a REST snapshot', () => {
    const state = createState()

    const result = reconcileRealtimeViolations(state, [
      {
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        updatedAt: '2026-08-20T10:05:00Z',
      },
    ])

    expect(result.byId['violation-1']).toMatchObject({
      eventId: 'violation-1',
      version: 1,
      cameraName: 'Kamera 1',
      departmentName: 'Kaynak',
      confidence: 0.94,
      clipReady: false,
      coverImageReady: false,
      dismissed: false,
      errorCode: null,
    })
  })

  it('keeps the newest snapshot when multiple recovery snapshots target the same violation', () => {
    const state = createState()

    const result = reconcileRealtimeViolations(state, [
      {
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        updatedAt: '2026-08-20T10:10:00Z',
      },
      {
        violationId: 'violation-1',
        lifecycleStatus: 'PREPARING',
        recordingStatus: 'PROCESSING',
        updatedAt: '2026-08-20T10:05:00Z',
      },
    ])

    expect(result.byId['violation-1']).toMatchObject({
      lifecycleStatus: 'COMPLETED',
      recordingStatus: 'READY',
      lastEventAt: '2026-08-20T10:10:00Z',
    })
  })
})
