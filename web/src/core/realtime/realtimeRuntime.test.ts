import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  RealtimeMessage,
  RealtimeMessageHandler,
  RealtimeRecoveryCallback,
} from './realtimeTypes'

interface RuntimeClientOptions {
  onMessage: RealtimeMessageHandler
  onRecoveryRequired?: RealtimeRecoveryCallback
}

const runtimeMock = vi.hoisted(() => ({
  clientOptions: null as unknown,
  cleanupAuthBinding: vi.fn(),
  bindRealtimeToAuth: vi.fn(),
}))

vi.mock('../../features/auth/authTokenProvider', () => ({
  authTokenProvider: {
    getAccessToken: vi.fn(),
    getSession: vi.fn(),
    getStatus: vi.fn(),
    subscribe: vi.fn(),
  },
}))

vi.mock('./RealtimeClient', () => ({
  RealtimeClient: class {
    constructor(options: unknown) {
      runtimeMock.clientOptions = options
    }

    connect = vi.fn()
    reconnectWithLatestToken = vi.fn()
    disconnect = vi.fn()
  },
}))

vi.mock('./realtimeAuthBridge', () => ({
  bindRealtimeToAuth: runtimeMock.bindRealtimeToAuth,
}))

import {
  startRealtimeRuntime,
  stopRealtimeRuntime,
  subscribeToRealtimeMessages,
  subscribeToRealtimeRecovery,
} from './realtimeRuntime'

function getClientOptions() {
  return runtimeMock.clientOptions as RuntimeClientOptions
}

describe('realtimeRuntime', () => {
  beforeEach(() => {
    runtimeMock.cleanupAuthBinding.mockReset()
    runtimeMock.bindRealtimeToAuth.mockReset()
    runtimeMock.bindRealtimeToAuth.mockReturnValue(runtimeMock.cleanupAuthBinding)
  })

  afterEach(() => {
    stopRealtimeRuntime()
  })

  it('creates only one auth binding while the runtime is started', () => {
    startRealtimeRuntime()
    startRealtimeRuntime()

    expect(runtimeMock.bindRealtimeToAuth).toHaveBeenCalledOnce()
  })

  it('cleans up the auth binding and can be started again', () => {
    startRealtimeRuntime()
    stopRealtimeRuntime()
    startRealtimeRuntime()

    expect(runtimeMock.cleanupAuthBinding).toHaveBeenCalledOnce()
    expect(runtimeMock.bindRealtimeToAuth).toHaveBeenCalledTimes(2)
  })

  it('publishes raw realtime messages to subscribers', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeToRealtimeMessages(listener)

    const message: RealtimeMessage = {
      body: '{"violationId":"violation-1"}',
      headers: {
        destination: '/user/queue/alerts',
      },
    }

    getClientOptions().onMessage(message)

    expect(listener).toHaveBeenCalledWith(message)

    unsubscribe()
    getClientOptions().onMessage(message)

    expect(listener).toHaveBeenCalledOnce()
  })

  it('notifies registered recovery subscribers', async () => {
    const recoveryListener = vi.fn()
    const unsubscribe = subscribeToRealtimeRecovery(recoveryListener)

    await getClientOptions().onRecoveryRequired?.()

    expect(recoveryListener).toHaveBeenCalledOnce()

    unsubscribe()
  })
})
