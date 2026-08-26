import { useSyncExternalStore } from 'react'
import { authTokenProvider } from './authTokenProvider'
import type { AuthSession, SessionStatus } from './sessionTypes'

function subscribe(onStoreChange: () => void) {
  return authTokenProvider.subscribe(() => {
    onStoreChange()
  })
}

export function useAuthSession(): {
  status: SessionStatus
  session: AuthSession | null
} {
  const status = useSyncExternalStore(
    subscribe,
    () => authTokenProvider.getStatus(),
    () => authTokenProvider.getStatus(),
  )

  const session = useSyncExternalStore(
    subscribe,
    () => authTokenProvider.getSession(),
    () => authTokenProvider.getSession(),
  )

  return {
    status,
    session,
  }
}
