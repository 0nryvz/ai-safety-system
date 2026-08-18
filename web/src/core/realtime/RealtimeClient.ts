import {
  Client,
  ReconnectionTimeMode,
  type IFrame,
  type IMessage,
  type StompSubscription,
} from '@stomp/stompjs'
import type { AuthTokenProvider } from '../../features/auth/sessionTypes'
import { ALERTS_DESTINATION, resolveWebSocketUrl } from './realtimeConfig'
import type {
  RealtimeConnectionListener,
  RealtimeConnectionStatus,
  RealtimeMessageHandler,
  RealtimeRecoveryCallback,
} from './realtimeTypes'

interface RealtimeClientOptions {
  authTokenProvider: AuthTokenProvider
  onMessage: RealtimeMessageHandler
  onRecoveryRequired?: RealtimeRecoveryCallback
  brokerUrl?: string
}

function isAuthenticationError(frame: IFrame) {
  const errorText = [frame.headers.message, frame.headers['error-code'], frame.body]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()

  return ['401', '403', 'unauthorized', 'forbidden', 'expired token', 'invalid token'].some(
    (value) => errorText.includes(value),
  )
}

export class RealtimeClient {
  private client: Client | null = null
  private subscription: StompSubscription | null = null
  private status: RealtimeConnectionStatus = 'OFFLINE'
  private readonly statusListeners = new Set<RealtimeConnectionListener>()
  private hasConnectedBefore = false
  private allowReconnect = false

  private readonly options: RealtimeClientOptions

  constructor(options: RealtimeClientOptions) {
    this.options = options
  }

  getStatus() {
    return this.status
  }

  subscribeToStatus(listener: RealtimeConnectionListener) {
    this.statusListeners.add(listener)

    return () => {
      this.statusListeners.delete(listener)
    }
  }

  connect() {
    const token = this.options.authTokenProvider.getAccessToken()

    if (!token || this.client?.active) {
      return
    }

    this.allowReconnect = true
    this.setStatus(this.hasConnectedBefore ? 'RECONNECTING' : 'CONNECTING')

    const client = new Client({
      brokerURL: this.options.brokerUrl ?? resolveWebSocketUrl(),
      reconnectDelay: 1_000,
      maxReconnectDelay: 30_000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      beforeConnect: (stompClient) => {
        const latestToken = this.options.authTokenProvider.getAccessToken()

        if (!latestToken) {
          this.allowReconnect = false
          stompClient.reconnectDelay = 0

          throw new Error('Realtime connection requires an authenticated session.')
        }

        stompClient.connectHeaders = {
          Authorization: `Bearer ${latestToken}`,
        }
      },
      onConnect: () => {
        const isRecovery = this.hasConnectedBefore

        this.hasConnectedBefore = true
        this.subscription?.unsubscribe()
        this.subscription = client.subscribe(ALERTS_DESTINATION, (message) =>
          this.handleMessage(message),
        )
        this.setStatus('CONNECTED')

        if (isRecovery) {
          void Promise.resolve(this.options.onRecoveryRequired?.()).catch(() => undefined)
        }
      },
      onDisconnect: () => {
        this.setStatus('OFFLINE')
      },
      onWebSocketClose: () => {
        this.subscription = null

        if (this.allowReconnect && this.options.authTokenProvider.getAccessToken()) {
          this.setStatus('RECONNECTING')
          return
        }

        this.setStatus('OFFLINE')
      },
      onStompError: (frame) => {
        if (isAuthenticationError(frame)) {
          this.allowReconnect = false
          client.reconnectDelay = 0
          void this.disconnect()
        }
      },
    })

    this.client = client
    client.activate()
  }

  async reconnectWithLatestToken() {
    await this.disconnect()
    this.connect()
  }

  async disconnect() {
    this.allowReconnect = false
    this.subscription?.unsubscribe()
    this.subscription = null

    const client = this.client
    this.client = null

    if (client?.active) {
      await client.deactivate()
    }

    this.setStatus('OFFLINE')
  }

  private handleMessage(message: IMessage) {
    this.options.onMessage({
      body: message.body,
      headers: { ...message.headers },
    })
  }

  private setStatus(status: RealtimeConnectionStatus) {
    if (this.status === status) {
      return
    }

    this.status = status
    this.statusListeners.forEach((listener) => {
      listener(status)
    })
  }
}
