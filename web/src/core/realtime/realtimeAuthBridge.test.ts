import { describe, expect, it, vi } from 'vitest'
import type {
  AuthTokenProvider,
  SessionChangeListener,
  SessionChangeReason,
} from '../../features/auth/sessionTypes'
import { bindRealtimeToAuth, type AuthAwareRealtimeClient } from './realtimeAuthBridge'

function createAuthProvider(
  status: 'anonymous' | 'authenticated' | 'expired',
  accessToken: string | null,
) {
  let listener: SessionChangeListener | null = null
  const unsubscribe = vi.fn()

  const provider: AuthTokenProvider = {
    getAccessToken: () => accessToken,
    getSession: () => null,
    getStatus: () => status,
    subscribe: (nextListener) => {
      listener = nextListener
      return unsubscribe
    },
  }

  return {
    provider,
    unsubscribe,
    emit(reason: SessionChangeReason) {
      listener?.(
        {
          status,
          session: null,
        },
        reason,
      )
    },
  }
}

function createRealtimeClient() {
  const client: AuthAwareRealtimeClient = {
    connect: vi.fn(),
    reconnectWithLatestToken: vi.fn().mockResolvedValue(undefined),
    disconnect: vi.fn().mockResolvedValue(undefined),
  }

  return client
}

describe('bindRealtimeToAuth', () => {
  it('connects immediately for a restored authenticated session', () => {
    const auth = createAuthProvider('authenticated', 'access-token')
    const client = createRealtimeClient()

    bindRealtimeToAuth(auth.provider, client)

    expect(client.connect).toHaveBeenCalledOnce()
  })

  it('does not connect immediately without an authenticated session', () => {
    const auth = createAuthProvider('anonymous', null)
    const client = createRealtimeClient()

    bindRealtimeToAuth(auth.provider, client)

    expect(client.connect).not.toHaveBeenCalled()
  })

  it('maps auth lifecycle events to realtime actions', () => {
    const auth = createAuthProvider('anonymous', null)
    const client = createRealtimeClient()

    const onSessionCleared = vi.fn()
    bindRealtimeToAuth(auth.provider, client, {
      onSessionCleared,
    })

    auth.emit('login')
    auth.emit('token-refresh')
    auth.emit('profile-update')
    auth.emit('logout')
    auth.emit('expiry')

    expect(client.connect).toHaveBeenCalledOnce()
    expect(client.reconnectWithLatestToken).toHaveBeenCalledOnce()
    expect(client.disconnect).toHaveBeenCalledTimes(2)
    expect(onSessionCleared).toHaveBeenCalledTimes(2)
  })

  it('unsubscribes and disconnects during cleanup', () => {
    const auth = createAuthProvider('anonymous', null)
    const client = createRealtimeClient()

    const cleanup = bindRealtimeToAuth(auth.provider, client)

    cleanup()

    expect(auth.unsubscribe).toHaveBeenCalledOnce()
    expect(client.disconnect).toHaveBeenCalledOnce()
  })
})
