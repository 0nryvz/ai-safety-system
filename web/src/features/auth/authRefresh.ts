import { refreshAuthTokens } from '../../services/authService'
import { authTokenProvider, clearSession, updateSessionTokens } from './authTokenProvider'
import type { AuthSession } from './sessionTypes'

let refreshPromise: Promise<AuthSession> | null = null

export function refreshSessionSingleFlight(): Promise<AuthSession> {
  const currentSession = authTokenProvider.getSession()

  if (!currentSession?.refreshToken) {
    clearSession('expiry')

    return Promise.reject(new Error('Refresh token is not available.'))
  }

  if (!refreshPromise) {
    refreshPromise = refreshAuthTokens(currentSession.refreshToken)
      .then((response) => {
        const latestSession = authTokenProvider.getSession()

        const refreshedSession: AuthSession = {
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          tokenType: response.tokenType,
          user: latestSession?.user ?? currentSession.user,
        }

        updateSessionTokens(refreshedSession)

        return refreshedSession
      })
      .catch((error: unknown) => {
        clearSession('expiry')
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}
