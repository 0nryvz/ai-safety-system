import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { realtimeEventStore } from './realtimeRuntime'
import { useRealtimeViolations } from './useRealtimeViolations'

const createAlertPayload = (violationId: string) =>
  JSON.stringify({
    violationId,
    type: 'MISSING_GLOVES',
    cameraName: 'Kamera 3',
    departmentName: 'Montaj',
    startedAt: '2026-08-17T12:00:00Z',
    confidence: 0.91,
    lifecycleStatus: 'ACTIVE',
    recordingStatus: 'REQUESTED',
    clipReady: false,
    coverImageReady: false,
  })

describe('useRealtimeViolations', () => {
  afterEach(() => {
    cleanup()
    realtimeEventStore.reset()
  })

  it('returns an empty violation list initially', () => {
    const { result } = renderHook(() => useRealtimeViolations())

    expect(result.current.violations).toEqual([])
  })

  it('updates when the realtime event store receives an alert', () => {
    const { result } = renderHook(() => useRealtimeViolations())

    act(() => {
      realtimeEventStore.ingest(createAlertPayload('violation-hook'))
    })

    expect(result.current.violations).toHaveLength(1)
    expect(result.current.violations[0]).toMatchObject({
      violationId: 'violation-hook',
      recordingStatus: 'REQUESTED',
      dismissed: false,
    })
  })

  it('dismisses a violation only in the client state', () => {
    const { result } = renderHook(() => useRealtimeViolations())

    act(() => {
      realtimeEventStore.ingest(createAlertPayload('violation-dismiss'))
    })

    act(() => {
      result.current.dismissViolation('violation-dismiss')
    })

    expect(result.current.violations[0]).toMatchObject({
      violationId: 'violation-dismiss',
      dismissed: true,
    })
  })
})
