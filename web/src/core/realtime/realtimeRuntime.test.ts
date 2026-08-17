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
  debugLogging: false,
}))

vi.mock('../../features/auth/authTokenProvider', () => ({
  authTokenProvider: {
    getAccessToken: vi.fn(),
    getSession: vi.fn(),
    getStatus: vi.fn(),
    subscribe: vi.fn(),
  },
}))

vi.mock('../../config/featureFlags', () => ({
  featureFlags: {
    get debugLogging() {
      return runtimeMock.debugLogging
    },
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
  realtimeEventStore,
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
    runtimeMock.debugLogging = false
    realtimeEventStore.reset()
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
  it('stores valid realtime messages in the central event store', () => {
    const message: RealtimeMessage = {
      body: JSON.stringify({
        violationId: 'violation-runtime',
        type: 'MISSING_GLOVES',
        cameraName: 'Kamera 3',
        departmentName: 'Montaj',
        startedAt: '2026-08-17T12:00:00Z',
        confidence: 0.91,
        lifecycleStatus: 'ACTIVE',
        recordingStatus: 'REQUESTED',
        clipReady: false,
        coverImageReady: false,
      }),
      headers: {
        destination: '/user/queue/alerts',
      },
    }

    getClientOptions().onMessage(message)

    expect(realtimeEventStore.getSnapshot().byId['violation-runtime']).toMatchObject({
      violationId: 'violation-runtime',
      recordingStatus: 'REQUESTED',
      dismissed: false,
    })
  })

  it('clears central event state when the auth session is cleared', () => {
    getClientOptions().onMessage({
      body: JSON.stringify({
        violationId: 'violation-runtime',
        type: 'MISSING_GLOVES',
        cameraName: 'Kamera 3',
        departmentName: 'Montaj',
        startedAt: '2026-08-17T12:00:00Z',
        confidence: 0.91,
        lifecycleStatus: 'ACTIVE',
        recordingStatus: 'REQUESTED',
        clipReady: false,
        coverImageReady: false,
      }),
      headers: {
        destination: '/user/queue/alerts',
      },
    })

    startRealtimeRuntime()

    const bindingOptions = runtimeMock.bindRealtimeToAuth.mock.calls[0]?.[2] as
      | {
          onSessionCleared?: () => void
        }
      | undefined

    bindingOptions?.onSessionCleared?.()

    expect(realtimeEventStore.getSnapshot().byId).toEqual({})
  })
  it('logs only a safe diagnostic when debug logging is enabled', () => {
    runtimeMock.debugLogging = true

    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    getClientOptions().onMessage({
      body: '{"accessToken":"must-not-be-logged"}',
      headers: {
        destination: '/user/queue/alerts',
      },
    })

    expect(warnSpy).toHaveBeenCalledOnce()
    expect(warnSpy).toHaveBeenCalledWith('[realtime] Realtime message rejected', 'INVALID_PAYLOAD')

    const loggedValues = warnSpy.mock.calls.flat().join(' ')

    expect(loggedValues).not.toContain('accessToken')
    expect(loggedValues).not.toContain('must-not-be-logged')

    warnSpy.mockRestore()
  })
})
