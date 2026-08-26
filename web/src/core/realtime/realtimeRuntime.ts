import { authTokenProvider } from '../../features/auth/authTokenProvider'
import { featureFlags } from '../../config/featureFlags'
import { RealtimeClient } from './RealtimeClient'
import { bindRealtimeToAuth } from './realtimeAuthBridge'
import type { RealtimeMessageHandler, RealtimeRecoveryCallback } from './realtimeTypes'
import { RealtimeEventStore } from './RealtimeEventStore'
import { recoverRealtimeState, type RealtimeRecoverySnapshotLoader } from './realtimeRecovery'

const messageListeners = new Set<RealtimeMessageHandler>()
const recoveryListeners = new Set<RealtimeRecoveryCallback>()
let recoverySnapshotLoader: RealtimeRecoverySnapshotLoader | null = null

export const realtimeEventStore = new RealtimeEventStore({
  onDiagnostic: (diagnostic) => {
    if (!featureFlags.debugLogging) {
      return
    }

    console.warn('[realtime] Realtime message rejected', diagnostic.code)
  },
})

function publishMessage(message: Parameters<RealtimeMessageHandler>[0]) {
  realtimeEventStore.ingest(message.body)

  messageListeners.forEach((listener) => {
    listener(message)
  })
}

async function publishRecoveryRequired() {
  if (recoverySnapshotLoader) {
    await recoverRealtimeState(realtimeEventStore, recoverySnapshotLoader)
  }

  const recoveryTasks = Array.from(recoveryListeners, (listener) =>
    Promise.resolve().then(listener),
  )

  await Promise.allSettled(recoveryTasks)
}

export const realtimeClient = new RealtimeClient({
  authTokenProvider,
  onMessage: publishMessage,
  onRecoveryRequired: publishRecoveryRequired,
})

let cleanupAuthBinding: (() => void) | null = null

export function startRealtimeRuntime() {
  if (cleanupAuthBinding) {
    return
  }

  cleanupAuthBinding = bindRealtimeToAuth(authTokenProvider, realtimeClient, {
    onSessionCleared: () => {
      realtimeEventStore.reset()
    },
  })
}

export function stopRealtimeRuntime() {
  const cleanup = cleanupAuthBinding
  cleanupAuthBinding = null
  cleanup?.()
}

export function subscribeToRealtimeMessages(listener: RealtimeMessageHandler) {
  messageListeners.add(listener)

  return () => {
    messageListeners.delete(listener)
  }
}

export function subscribeToRealtimeRecovery(listener: RealtimeRecoveryCallback) {
  recoveryListeners.add(listener)

  return () => {
    recoveryListeners.delete(listener)
  }
}

export function setRealtimeRecoverySnapshotLoader(loader: RealtimeRecoverySnapshotLoader | null) {
  recoverySnapshotLoader = loader
}

export function clearRealtimeRecoverySnapshotLoader() {
  recoverySnapshotLoader = null
}
