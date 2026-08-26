import type { RealtimeRecoverySnapshot } from './realtimeRecoveryReconciler'
import type { RealtimeEventStore } from './RealtimeEventStore'

export type RealtimeRecoverySnapshotLoader = () => Promise<RealtimeRecoverySnapshot[]>

export async function recoverRealtimeState(
  store: RealtimeEventStore,
  loadSnapshots: RealtimeRecoverySnapshotLoader,
): Promise<boolean> {
  const snapshots = await loadSnapshots()

  return store.reconcile(snapshots)
}
