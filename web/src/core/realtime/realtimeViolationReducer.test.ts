import { describe, expect, it } from 'vitest'
import { initialRealtimeViolationState, realtimeViolationReducer } from './realtimeViolationReducer'
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

describe('realtimeViolationReducer', () => {
  it('creates a violation record from an initial alert', () => {
    const state = realtimeViolationReducer(initialRealtimeViolationState, {
      type: 'EVENT_RECEIVED',
      event: alert,
    })

    expect(state.byId['violation-1']).toEqual({
      ...alert.payload,
      lastEventAt: alert.payload.startedAt,
      dismissed: false,
      errorCode: null,
    })
  })

  it('applies an update to the existing violation record', () => {
    const alertState = realtimeViolationReducer(initialRealtimeViolationState, {
      type: 'EVENT_RECEIVED',
      event: alert,
    })

    const updatedState = realtimeViolationReducer(alertState, {
      type: 'EVENT_RECEIVED',
      event: update,
    })

    expect(Object.keys(updatedState.byId)).toEqual(['violation-1'])
    expect(updatedState.byId['violation-1']).toEqual({
      ...alert.payload,
      lifecycleStatus: 'COMPLETED',
      recordingStatus: 'READY',
      clipReady: true,
      lastEventAt: update.payload.updatedAt,
      dismissed: false,
      errorCode: null,
    })
  })
  it('does not allow an older update to roll back newer state', () => {
    const alertState = realtimeViolationReducer(initialRealtimeViolationState, {
      type: 'EVENT_RECEIVED',
      event: alert,
    })

    const readyState = realtimeViolationReducer(alertState, {
      type: 'EVENT_RECEIVED',
      event: update,
    })

    const olderUpdate: ParsedRealtimePayload = {
      kind: 'VIOLATION_UPDATE',
      payload: {
        violationId: 'violation-1',
        lifecycleStatus: 'PREPARING',
        recordingStatus: 'PROCESSING',
        clipReady: false,
        updatedAt: '2026-08-17T12:00:30Z',
        errorCode: null,
      },
    }

    const result = realtimeViolationReducer(readyState, {
      type: 'EVENT_RECEIVED',
      event: olderUpdate,
    })

    expect(result).toBe(readyState)
    expect(result.byId['violation-1'].recordingStatus).toBe('READY')
    expect(result.byId['violation-1'].clipReady).toBe(true)
  })

  it('keeps the violation record when the user dismisses it', () => {
    const alertState = realtimeViolationReducer(initialRealtimeViolationState, {
      type: 'EVENT_RECEIVED',
      event: alert,
    })

    const dismissedState = realtimeViolationReducer(alertState, {
      type: 'DISMISS',
      violationId: 'violation-1',
    })

    expect(Object.keys(dismissedState.byId)).toEqual(['violation-1'])
    expect(dismissedState.byId['violation-1'].dismissed).toBe(true)
  })

  it('does not create a partial record when an update arrives before its alert', () => {
    const state = realtimeViolationReducer(initialRealtimeViolationState, {
      type: 'EVENT_RECEIVED',
      event: update,
    })

    expect(state).toBe(initialRealtimeViolationState)
    expect(state.byId).toEqual({})
  })

  it('keeps different violation records isolated', () => {
    const secondAlert: ParsedRealtimePayload = {
      kind: 'ALERT',
      payload: {
        ...alert.payload,
        violationId: 'violation-2',
        cameraName: 'Kamera 2',
      },
    }

    const firstState = realtimeViolationReducer(initialRealtimeViolationState, {
      type: 'EVENT_RECEIVED',
      event: alert,
    })

    const secondState = realtimeViolationReducer(firstState, {
      type: 'EVENT_RECEIVED',
      event: secondAlert,
    })

    expect(Object.keys(secondState.byId)).toEqual(['violation-1', 'violation-2'])
    expect(secondState.byId['violation-1'].cameraName).toBe('Kamera 1')
    expect(secondState.byId['violation-2'].cameraName).toBe('Kamera 2')
  })
})
