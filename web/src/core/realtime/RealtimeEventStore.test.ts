import { describe, expect, it, vi } from 'vitest'
import { RealtimeEventStore } from './RealtimeEventStore'

const alertBody = JSON.stringify({
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
})

describe('RealtimeEventStore', () => {
  it('parses and stores a valid alert', () => {
    const store = new RealtimeEventStore()
    const listener = vi.fn()

    store.subscribe(listener)

    expect(store.ingest(alertBody)).toBe(true)
    expect(store.getSnapshot().byId['violation-1']).toMatchObject({
      violationId: 'violation-1',
      recordingStatus: 'REQUESTED',
      dismissed: false,
    })
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('does not publish the same event twice', () => {
    const store = new RealtimeEventStore()
    const listener = vi.fn()

    store.subscribe(listener)

    expect(store.ingest(alertBody)).toBe(true)
    expect(store.ingest(alertBody)).toBe(false)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('rejects an invalid payload and emits a safe diagnostic', () => {
    const onDiagnostic = vi.fn()
    const store = new RealtimeEventStore({
      onDiagnostic,
    })
    const listener = vi.fn()

    store.subscribe(listener)

    expect(store.ingest('{invalid-json')).toBe(false)
    expect(store.getSnapshot().byId).toEqual({})
    expect(listener).not.toHaveBeenCalled()
    expect(onDiagnostic).toHaveBeenCalledOnce()
    expect(onDiagnostic).toHaveBeenCalledWith({
      code: 'INVALID_PAYLOAD',
    })
    expect(onDiagnostic).not.toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.anything(),
      }),
    )
  })

  it('stores unknown enum values with a safe diagnostic', () => {
    const onDiagnostic = vi.fn()
    const store = new RealtimeEventStore({
      onDiagnostic,
    })

    const unknownEnumBody = JSON.stringify({
      violationId: 'violation-unknown',
      type: 'NEW_BACKEND_VIOLATION_TYPE',
      cameraName: 'Kamera 2',
      departmentName: 'Montaj',
      startedAt: '2026-08-17T12:00:00Z',
      confidence: 0.82,
      lifecycleStatus: 'NEW_LIFECYCLE_STATUS',
      recordingStatus: 'NEW_RECORDING_STATUS',
      clipReady: false,
      coverImageReady: false,
    })

    expect(store.ingest(unknownEnumBody)).toBe(true)
    expect(store.getSnapshot().byId['violation-unknown']).toMatchObject({
      violationId: 'violation-unknown',
      type: 'UNKNOWN',
      lifecycleStatus: 'UNKNOWN',
      recordingStatus: 'UNKNOWN',
    })
    expect(onDiagnostic).toHaveBeenCalledOnce()
    expect(onDiagnostic).toHaveBeenCalledWith({
      code: 'UNKNOWN_ENUM_VALUE',
    })
  })

  it('dismisses an alert without deleting its violation record', () => {
    const store = new RealtimeEventStore()

    store.ingest(alertBody)

    expect(store.dismiss('violation-1')).toBe(true)
    expect(store.getSnapshot().byId['violation-1']).toMatchObject({
      violationId: 'violation-1',
      dismissed: true,
    })
  })

  it('clears state and duplicate history on reset', () => {
    const store = new RealtimeEventStore()

    store.ingest(alertBody)
    store.reset()

    expect(store.getSnapshot().byId).toEqual({})
    expect(store.ingest(alertBody)).toBe(true)
  })

  it('reconciles the store with a newer recovery snapshot', () => {
    const store = new RealtimeEventStore()
    const listener = vi.fn()

    store.subscribe(listener)
    store.ingest(alertBody)
    listener.mockClear()

    expect(
      store.reconcile([
        {
          violationId: 'violation-1',
          lifecycleStatus: 'COMPLETED',
          recordingStatus: 'READY',
          updatedAt: '2026-08-17T12:05:00Z',
        },
      ]),
    ).toBe(true)

    expect(store.getSnapshot().byId['violation-1']).toMatchObject({
      lifecycleStatus: 'COMPLETED',
      recordingStatus: 'READY',
      lastEventAt: '2026-08-17T12:05:00Z',
    })

    expect(listener).toHaveBeenCalledOnce()
  })

  it('does not publish when a recovery snapshot does not change the store', () => {
    const store = new RealtimeEventStore()
    const listener = vi.fn()

    store.subscribe(listener)
    store.ingest(alertBody)
    listener.mockClear()

    expect(
      store.reconcile([
        {
          violationId: 'violation-1',
          lifecycleStatus: 'COMPLETED',
          recordingStatus: 'READY',
          updatedAt: '2026-08-17T11:59:00Z',
        },
      ]),
    ).toBe(false)

    expect(listener).not.toHaveBeenCalled()
  })
})
