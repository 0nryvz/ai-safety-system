import { getViolationHistory, type ViolationListItem } from '../../services/violationService'
import type { RealtimeRecoverySnapshot } from './realtimeRecoveryReconciler'

function toRecoverySnapshot(violation: ViolationListItem): RealtimeRecoverySnapshot | null {
  if (!violation.recordingStatus) {
    return null
  }

  return {
    violationId: violation.violationId,
    lifecycleStatus: violation.lifecycleStatus,
    recordingStatus: violation.recordingStatus,
    updatedAt: violation.updatedAt,
  }
}

export async function loadRealtimeRecoverySnapshots(): Promise<RealtimeRecoverySnapshot[]> {
  const response = await getViolationHistory({
    lifecycleStatus: 'ACTIVE',
    page: 0,
    size: 100,
    sort: ['updatedAt,desc'],
  })

  return response.content
    .map(toRecoverySnapshot)
    .filter((snapshot): snapshot is RealtimeRecoverySnapshot => snapshot !== null)
}
