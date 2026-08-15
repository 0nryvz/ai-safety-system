import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authService from '../../services/authService'
import { authTokenProvider, clearSession, setAuthenticatedSession } from './authTokenProvider'
import { refreshSessionSingleFlight } from './authRefresh'
import type { AuthSession } from './sessionTypes'

const session: AuthSession = {
  accessToken: 'old-access-token',
  refreshToken: 'old-refresh-token',
  tokenType: 'Bearer',
  user: null,
}

beforeEach(() => {
  window.sessionStorage.clear()
  clearSession('logout')
})

afterEach(() => {
  vi.restoreAllMocks()
  window.sessionStorage.clear()
  clearSession('logout')
})

describe('refreshSessionSingleFlight', () => {
  it('refreshes and updates the current session', async () => {
    setAuthenticatedSession(session)

    vi.spyOn(authService, 'refreshAuthTokens').mockResolvedValue({
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
    })

    const result = await refreshSessionSingleFlight()

    expect(result.accessToken).toBe('new-access-token')
    expect(authTokenProvider.getAccessToken()).toBe('new-access-token')
    expect(authTokenProvider.getSession()?.refreshToken).toBe('new-refresh-token')
  })

  it('uses only one refresh request while a refresh is already running', async () => {
    setAuthenticatedSession(session)

    let resolveRefresh: ((value: authService.AuthResponse) => void) | undefined

    const refreshRequest = new Promise<authService.AuthResponse>((resolve) => {
      resolveRefresh = resolve
    })

    const refreshSpy = vi.spyOn(authService, 'refreshAuthTokens').mockReturnValue(refreshRequest)

    const firstRefresh = refreshSessionSingleFlight()
    const secondRefresh = refreshSessionSingleFlight()
    const thirdRefresh = refreshSessionSingleFlight()

    expect(refreshSpy).toHaveBeenCalledTimes(1)

    resolveRefresh?.({
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      tokenType: 'Bearer',
    })

    const results = await Promise.all([firstRefresh, secondRefresh, thirdRefresh])

    expect(results).toHaveLength(3)
    expect(refreshSpy).toHaveBeenCalledTimes(1)
    expect(authTokenProvider.getAccessToken()).toBe('new-access-token')
  })

  it('expires the session when refresh fails', async () => {
    setAuthenticatedSession(session)

    vi.spyOn(authService, 'refreshAuthTokens').mockRejectedValue(new Error('Refresh failed'))

    await expect(refreshSessionSingleFlight()).rejects.toThrow('Refresh failed')

    expect(authTokenProvider.getStatus()).toBe('expired')
    expect(authTokenProvider.getSession()).toBeNull()
  })

  it('expires immediately when no refresh token is available', async () => {
    await expect(refreshSessionSingleFlight()).rejects.toThrow('Refresh token is not available.')

    expect(authTokenProvider.getStatus()).toBe('expired')
  })
})
