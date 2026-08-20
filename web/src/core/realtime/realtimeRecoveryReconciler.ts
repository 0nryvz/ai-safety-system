import type { RealtimeLifecycleStatus, RealtimeRecordingStatus } from './realtimeTypes'
import type { RealtimeViolationRecord, RealtimeViolationState } from './realtimeViolationReducer'

export interface RealtimeRecoverySnapshot {
  violationId: string
  lifecycleStatus: RealtimeLifecycleStatus
  recordingStatus: RealtimeRecordingStatus
  updatedAt: string
}

function parseTimestamp(value: string): number | null {
  const timestamp = Date.parse(value)

  return Number.isNaN(timestamp) ? null : timestamp
}

function reconcileViolation(
  current: RealtimeViolationRecord,
  snapshot: RealtimeRecoverySnapshot,
): RealtimeViolationRecord {
  const currentTimestamp = parseTimestamp(current.lastEventAt)
  const snapshotTimestamp = parseTimestamp(snapshot.updatedAt)

  if (
    currentTimestamp === null ||
    snapshotTimestamp === null ||
    snapshotTimestamp <= currentTimestamp
  ) {
    return current
  }

  return {
    ...current,
    lifecycleStatus: snapshot.lifecycleStatus,
    recordingStatus: snapshot.recordingStatus,
    lastEventAt: snapshot.updatedAt,
  }
}

export function reconcileRealtimeViolations(
  state: RealtimeViolationState,
  snapshots: RealtimeRecoverySnapshot[],
): RealtimeViolationState {
  let nextById = state.byId
  let changed = false

  for (const snapshot of snapshots) {
    const current = nextById[snapshot.violationId]

    if (!current) {
      continue
    }

    const reconciled = reconcileViolation(current, snapshot)

    if (reconciled === current) {
      continue
    }

    if (!changed) {
      nextById = {
        ...state.byId,
      }
      changed = true
    }

    nextById[snapshot.violationId] = reconciled
  }

  if (!changed) {
    return state
  }

  return {
    ...state,
    byId: nextById,
  }
}
