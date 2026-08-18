import type { AuthTokenProvider } from '../../features/auth/sessionTypes'

export interface AuthAwareRealtimeClient {
  connect(): void
  reconnectWithLatestToken(): Promise<void>
  disconnect(): Promise<void>
}

export interface RealtimeAuthBindingOptions {
  onSessionCleared?: () => void
}

export function bindRealtimeToAuth(
  authTokenProvider: AuthTokenProvider,
  realtimeClient: AuthAwareRealtimeClient,
  options: RealtimeAuthBindingOptions = {},
) {
  function connectIfAuthenticated() {
    if (authTokenProvider.getStatus() === 'authenticated' && authTokenProvider.getAccessToken()) {
      realtimeClient.connect()
    }
  }

  connectIfAuthenticated()

  const unsubscribe = authTokenProvider.subscribe((_snapshot, reason) => {
    switch (reason) {
      case 'login':
        realtimeClient.connect()
        break

      case 'token-refresh':
        void realtimeClient.reconnectWithLatestToken()
        break

      case 'logout':
      case 'expiry':
        options.onSessionCleared?.()
        void realtimeClient.disconnect()
        break

      case 'profile-update':
        break
    }
  })

  return () => {
    unsubscribe()
    void realtimeClient.disconnect()
  }
}
