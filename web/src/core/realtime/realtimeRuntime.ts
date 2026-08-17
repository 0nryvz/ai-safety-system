import { authTokenProvider } from '../../features/auth/authTokenProvider'
import { RealtimeClient } from './RealtimeClient'
import { bindRealtimeToAuth } from './realtimeAuthBridge'
import type { RealtimeMessageHandler, RealtimeRecoveryCallback } from './realtimeTypes'

const messageListeners = new Set<RealtimeMessageHandler>()
const recoveryListeners = new Set<RealtimeRecoveryCallback>()

function publishMessage(message: Parameters<RealtimeMessageHandler>[0]) {
  messageListeners.forEach((listener) => {
    listener(message)
  })
}

async function publishRecoveryRequired() {
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

  cleanupAuthBinding = bindRealtimeToAuth(authTokenProvider, realtimeClient)
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
