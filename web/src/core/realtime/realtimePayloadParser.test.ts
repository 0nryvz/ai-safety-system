import { describe, expect, it } from 'vitest'
import { parseRealtimePayload } from './realtimePayloadParser'

describe('parseRealtimePayload', () => {
  it('parses a valid initial alert', () => {
    const result = parseRealtimePayload(
      JSON.stringify({
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
      }),
    )

    expect(result).toEqual({
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
    })
  })

  it('parses a valid violation update', () => {
    const result = parseRealtimePayload(
      JSON.stringify({
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        clipReady: true,
        updatedAt: '2026-08-17T12:01:00Z',
        errorCode: null,
      }),
    )

    expect(result).toEqual({
      kind: 'VIOLATION_UPDATE',
      payload: {
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        clipReady: true,
        updatedAt: '2026-08-17T12:01:00Z',
        errorCode: null,
      },
    })
  })

  it('rejects malformed JSON', () => {
    expect(parseRealtimePayload('{invalid-json')).toBeNull()
  })

  it('rejects a payload without violationId', () => {
    expect(
      parseRealtimePayload(
        JSON.stringify({
          lifecycleStatus: 'ACTIVE',
          recordingStatus: 'REQUESTED',
          clipReady: false,
          updatedAt: '2026-08-17T12:01:00Z',
        }),
      ),
    ).toBeNull()
  })
  it('maps unknown enum values to UNKNOWN without crashing', () => {
    const result = parseRealtimePayload(
      JSON.stringify({
        violationId: 'violation-unknown',
        type: 'NEW_PPE_TYPE',
        cameraName: 'Kamera 2',
        departmentName: 'Montaj',
        startedAt: '2026-08-17T12:00:00Z',
        confidence: 0.82,
        lifecycleStatus: 'NEW_LIFECYCLE_STATUS',
        recordingStatus: 'NEW_RECORDING_STATUS',
        clipReady: false,
        coverImageReady: false,
      }),
    )

    expect(result).toEqual({
      kind: 'ALERT',
      payload: {
        violationId: 'violation-unknown',
        type: 'UNKNOWN',
        cameraName: 'Kamera 2',
        departmentName: 'Montaj',
        startedAt: '2026-08-17T12:00:00Z',
        confidence: 0.82,
        lifecycleStatus: 'UNKNOWN',
        recordingStatus: 'UNKNOWN',
        clipReady: false,
        coverImageReady: false,
      },
    })
  })
})
