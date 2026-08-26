import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthTokenProvider } from '../../features/auth/sessionTypes'
import { ALERTS_DESTINATION } from './realtimeConfig'
import { RealtimeClient } from './RealtimeClient'

interface MockStompMessage {
  body: string
  headers: Record<string, string>
}

interface MockStompConfig {
  brokerURL?: string
  reconnectDelay?: number
  maxReconnectDelay?: number
  reconnectTimeMode?: string
  beforeConnect?: (client: MockStompClient) => void | Promise<void>
  onConnect?: () => void
  onDisconnect?: () => void
  onWebSocketClose?: () => void
  onStompError?: (frame: { headers: Record<string, string>; body: string }) => void
}

interface MockSubscription {
  unsubscribe: ReturnType<typeof vi.fn>
}

interface MockStompClient {
  active: boolean
  reconnectDelay: number
  connectHeaders: Record<string, string>
  config: MockStompConfig
  activate: ReturnType<typeof vi.fn>
  deactivate: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn>
  subscription: MockSubscription
  messageHandler: ((message: MockStompMessage) => void) | null
}

const stompMock = vi.hoisted(() => ({
  instances: [] as MockStompClient[],
}))

vi.mock('@stomp/stompjs', () => {
  class Client {
    active = false
    reconnectDelay = 0
    connectHeaders: Record<string, string> = {}
    config: MockStompConfig
    subscription = {
      unsubscribe: vi.fn(),
    }
    messageHandler: ((message: MockStompMessage) => void) | null = null

    activate = vi.fn(() => {
      this.active = true
    })

    deactivate = vi.fn(async () => {
      this.active = false
    })

    subscribe = vi.fn((_destination: string, handler: (message: MockStompMessage) => void) => {
      this.messageHandler = handler
      return this.subscription
    })

    constructor(config: MockStompConfig) {
      this.config = config
      stompMock.instances.push(this)
    }
  }

  return {
    Client,
    ReconnectionTimeMode: {
      EXPONENTIAL: 'EXPONENTIAL',
    },
  }
})

function createAuthTokenProvider(getAccessToken: () => string | null): AuthTokenProvider {
  return {
    getAccessToken,
    getSession: () => null,
    getStatus: () => (getAccessToken() ? 'authenticated' : 'anonymous'),
    subscribe: () => () => undefined,
  }
}

describe('RealtimeClient', () => {
  beforeEach(() => {
    stompMock.instances.length = 0
  })

  it('does not create a STOMP client without an access token', () => {
    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => null),
      onMessage: vi.fn(),
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    expect(stompMock.instances).toHaveLength(0)
    expect(realtimeClient.getStatus()).toBe('OFFLINE')
  })

  it('creates and activates the STOMP client with the expected configuration', () => {
    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => 'access-token'),
      onMessage: vi.fn(),
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    const stompClient = stompMock.instances[0]

    expect(stompClient.config.brokerURL).toBe('ws://localhost:8080/ws')
    expect(stompClient.config.reconnectDelay).toBe(1_000)
    expect(stompClient.config.maxReconnectDelay).toBe(30_000)
    expect(stompClient.config.reconnectTimeMode).toBe('EXPONENTIAL')
    expect(stompClient.activate).toHaveBeenCalledOnce()
    expect(realtimeClient.getStatus()).toBe('CONNECTING')
  })

  it('uses the latest token, subscribes to alerts and forwards messages', async () => {
    let accessToken = 'initial-token'
    const onMessage = vi.fn()

    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => accessToken),
      onMessage,
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    const stompClient = stompMock.instances[0]

    accessToken = 'latest-token'
    await stompClient.config.beforeConnect?.(stompClient)

    expect(stompClient.connectHeaders).toEqual({
      Authorization: 'Bearer latest-token',
    })

    stompClient.config.onConnect?.()

    expect(stompClient.subscribe).toHaveBeenCalledWith(ALERTS_DESTINATION, expect.any(Function))
    expect(realtimeClient.getStatus()).toBe('CONNECTED')

    stompClient.messageHandler?.({
      body: '{"violationId":"violation-1"}',
      headers: {
        destination: ALERTS_DESTINATION,
      },
    })

    expect(onMessage).toHaveBeenCalledWith({
      body: '{"violationId":"violation-1"}',
      headers: {
        destination: ALERTS_DESTINATION,
      },
    })
  })
  it('disconnects, removes the subscription and becomes offline', async () => {
    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => 'access-token'),
      onMessage: vi.fn(),
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    const stompClient = stompMock.instances[0]

    stompClient.config.onConnect?.()

    expect(realtimeClient.getStatus()).toBe('CONNECTED')

    await realtimeClient.disconnect()

    expect(stompClient.subscription.unsubscribe).toHaveBeenCalledOnce()
    expect(stompClient.deactivate).toHaveBeenCalledOnce()
    expect(realtimeClient.getStatus()).toBe('OFFLINE')
  })

  it('reconnects with a newly created STOMP client after token refresh', async () => {
    let accessToken = 'initial-token'

    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => accessToken),
      onMessage: vi.fn(),
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    const firstClient = stompMock.instances[0]

    firstClient.config.onConnect?.()
    accessToken = 'refreshed-token'

    await realtimeClient.reconnectWithLatestToken()

    expect(firstClient.deactivate).toHaveBeenCalledOnce()
    expect(stompMock.instances).toHaveLength(2)

    const secondClient = stompMock.instances[1]

    expect(secondClient.activate).toHaveBeenCalledOnce()
    expect(realtimeClient.getStatus()).toBe('RECONNECTING')

    await secondClient.config.beforeConnect?.(secondClient)

    expect(secondClient.connectHeaders).toEqual({
      Authorization: 'Bearer refreshed-token',
    })
  })

  it('stops reconnecting and disconnects after a STOMP authentication error', async () => {
    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => 'expired-token'),
      onMessage: vi.fn(),
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    const stompClient = stompMock.instances[0]

    stompClient.config.onStompError?.({
      headers: {
        message: '401 Unauthorized',
      },
      body: '',
    })

    await vi.waitFor(() => {
      expect(stompClient.deactivate).toHaveBeenCalledOnce()
    })

    expect(stompClient.reconnectDelay).toBe(0)
    expect(realtimeClient.getStatus()).toBe('OFFLINE')
  })
  it('reports reconnecting after an unexpected WebSocket close', () => {
    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => 'access-token'),
      onMessage: vi.fn(),
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    const stompClient = stompMock.instances[0]

    stompClient.config.onConnect?.()
    stompClient.config.onWebSocketClose?.()

    expect(realtimeClient.getStatus()).toBe('RECONNECTING')
  })

  it('requests REST recovery only after a successful reconnection', () => {
    const onRecoveryRequired = vi.fn()

    const realtimeClient = new RealtimeClient({
      authTokenProvider: createAuthTokenProvider(() => 'access-token'),
      onMessage: vi.fn(),
      onRecoveryRequired,
      brokerUrl: 'ws://localhost:8080/ws',
    })

    realtimeClient.connect()

    const stompClient = stompMock.instances[0]

    stompClient.config.onConnect?.()

    expect(onRecoveryRequired).not.toHaveBeenCalled()

    stompClient.config.onWebSocketClose?.()
    stompClient.config.onConnect?.()

    expect(onRecoveryRequired).toHaveBeenCalledOnce()
    expect(realtimeClient.getStatus()).toBe('CONNECTED')
  })
})
