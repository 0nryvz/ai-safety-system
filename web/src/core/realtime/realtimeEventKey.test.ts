import { describe, expect, it } from 'vitest'
import { createRealtimeEventKey } from './realtimeEventKey'
import type { ParsedRealtimePayload } from './realtimePayloadParser'

const alert: ParsedRealtimePayload = {
  kind: 'ALERT',
  payload: {
    violationId: 'violation-1',
    type: 'MISSING_WELDING_MASK',
    cameraName: 'Kamera 1',
    departmentName: 'Kaynak',
    startedAt: '2026-08-17T12:00:00Z',
    confidence: 0.94,
    lifecycleStatus: 'ACTIVE',
    recordingStatus: 'REQUESTED',
    clipReady: false,
    coverImageReady: false,
  },
}

const update: ParsedRealtimePayload = {
  kind: 'VIOLATION_UPDATE',
  payload: {
    violationId: 'violation-1',
    lifecycleStatus: 'COMPLETED',
    recordingStatus: 'READY',
    clipReady: true,
    updatedAt: '2026-08-17T12:01:00Z',
    errorCode: null,
  },
}

describe('createRealtimeEventKey', () => {
  it('creates the same key for the same alert', () => {
    expect(createRealtimeEventKey(alert)).toBe(createRealtimeEventKey(alert))
  })

  it('creates the same key for the same update', () => {
    expect(createRealtimeEventKey(update)).toBe(createRealtimeEventKey(update))
  })

  it('creates different keys for different update states', () => {
    const changedUpdate: ParsedRealtimePayload = {
      kind: 'VIOLATION_UPDATE',
      payload: {
        ...update.payload,
        lifecycleStatus: 'ERROR',
        recordingStatus: 'ERROR',
        clipReady: false,
        errorCode: 'RECORDING_FAILED',
      },
    }

    expect(createRealtimeEventKey(update)).not.toBe(createRealtimeEventKey(changedUpdate))
  })

  it('does not confuse an alert with an update for the same violation', () => {
    expect(createRealtimeEventKey(alert)).not.toBe(createRealtimeEventKey(update))
  })
})
