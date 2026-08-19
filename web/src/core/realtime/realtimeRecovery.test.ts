import { describe, expect, it, vi } from 'vitest'
import { RealtimeEventStore } from './RealtimeEventStore'
import { recoverRealtimeState } from './realtimeRecovery'

const alertBody = JSON.stringify({
  violationId: 'violation-1',
  type: 'MISSING_WELDING_MASK',
  cameraName: 'Kamera 1',
  departmentName: 'Kaynak',
  startedAt: '2026-08-20T10:00:00Z',
  confidence: 0.94,
  lifecycleStatus: 'ACTIVE',
  recordingStatus: 'REQUESTED',
  clipReady: false,
  coverImageReady: false,
})

describe('recoverRealtimeState', () => {
  it('loads recovery snapshots and reconciles the store', async () => {
    const store = new RealtimeEventStore()
    store.ingest(alertBody)

    const loadSnapshots = vi.fn().mockResolvedValue([
      {
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        updatedAt: '2026-08-20T10:05:00Z',
      },
    ])

    const changed = await recoverRealtimeState(store, loadSnapshots)

    expect(loadSnapshots).toHaveBeenCalledOnce()
    expect(changed).toBe(true)
    expect(store.getSnapshot().byId['violation-1']).toMatchObject({
      lifecycleStatus: 'COMPLETED',
      recordingStatus: 'READY',
      lastEventAt: '2026-08-20T10:05:00Z',
    })
  })

  it('returns false when recovery snapshots do not change the store', async () => {
    const store = new RealtimeEventStore()
    store.ingest(alertBody)

    const loadSnapshots = vi.fn().mockResolvedValue([
      {
        violationId: 'violation-1',
        lifecycleStatus: 'ACTIVE',
        recordingStatus: 'REQUESTED',
        updatedAt: '2026-08-20T10:00:00Z',
      },
    ])

    await expect(recoverRealtimeState(store, loadSnapshots)).resolves.toBe(false)
  })

  it('does not swallow snapshot loader failures', async () => {
    const store = new RealtimeEventStore()
    const error = new Error('recovery failed')

    const loadSnapshots = vi.fn().mockRejectedValue(error)

    await expect(recoverRealtimeState(store, loadSnapshots)).rejects.toBe(error)
  })

  it('does not overwrite a newer realtime event that arrives while recovery is loading', async () => {
    const store = new RealtimeEventStore()
    store.ingest(alertBody)

    let resolveSnapshots: ((snapshots: Parameters<typeof store.reconcile>[0]) => void) | undefined

    const loadSnapshots = vi.fn(
      () =>
        new Promise<Parameters<typeof store.reconcile>[0]>((resolve) => {
          resolveSnapshots = resolve
        }),
    )

    const recoveryPromise = recoverRealtimeState(store, loadSnapshots)

    store.ingest(
      JSON.stringify({
        violationId: 'violation-1',
        lifecycleStatus: 'COMPLETED',
        recordingStatus: 'READY',
        clipReady: true,
        updatedAt: '2026-08-20T10:10:00Z',
      }),
    )

    resolveSnapshots?.([
      {
        violationId: 'violation-1',
        lifecycleStatus: 'PREPARING',
        recordingStatus: 'PROCESSING',
        updatedAt: '2026-08-20T10:05:00Z',
      },
    ])

    await recoveryPromise

    expect(store.getSnapshot().byId['violation-1']).toMatchObject({
      lifecycleStatus: 'COMPLETED',
      recordingStatus: 'READY',
      clipReady: true,
      lastEventAt: '2026-08-20T10:10:00Z',
    })
  })
})
