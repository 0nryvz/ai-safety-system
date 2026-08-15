import { describe, expect, it, vi } from 'vitest'
import {
  authTokenProvider,
  clearSession,
  setAuthenticatedSession,
  updateSessionTokens,
} from './authTokenProvider'
import type { AuthSession } from './sessionTypes'

const session: AuthSession = {
  accessToken: 'access-token-1',
  refreshToken: 'refresh-token-1',
  tokenType: 'Bearer',
  user: {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'user@example.com',
    fullName: 'Test User',
    active: true,
    roles: ['OHS_SPECIALIST'],
    departmentIds: ['22222222-2222-2222-2222-222222222222'],
  },
}

describe('authTokenProvider', () => {
  it('exposes the authenticated session and access token', () => {
    setAuthenticatedSession(session)

    expect(authTokenProvider.getStatus()).toBe('authenticated')
    expect(authTokenProvider.getAccessToken()).toBe('access-token-1')
    expect(authTokenProvider.getSession()).toEqual(session)

    clearSession('logout')
  })

  it('notifies subscribers when the session changes', () => {
    const listener = vi.fn()
    const unsubscribe = authTokenProvider.subscribe(listener)

    setAuthenticatedSession(session)

    expect(listener).toHaveBeenCalledWith(
      {
        status: 'authenticated',
        session,
      },
      'login',
    )

    unsubscribe()
    clearSession('logout')
  })

  it('notifies token refresh with the updated session', () => {
    setAuthenticatedSession(session)

    const listener = vi.fn()
    const unsubscribe = authTokenProvider.subscribe(listener)

    const refreshedSession: AuthSession = {
      ...session,
      accessToken: 'access-token-2',
      refreshToken: 'refresh-token-2',
    }

    updateSessionTokens(refreshedSession)

    expect(authTokenProvider.getAccessToken()).toBe('access-token-2')
    expect(listener).toHaveBeenCalledWith(
      {
        status: 'authenticated',
        session: refreshedSession,
      },
      'token-refresh',
    )

    unsubscribe()
    clearSession('logout')
  })

  it('clears the session on expiry', () => {
    setAuthenticatedSession(session)

    clearSession('expiry')

    expect(authTokenProvider.getStatus()).toBe('expired')
    expect(authTokenProvider.getAccessToken()).toBeNull()
    expect(authTokenProvider.getSession()).toBeNull()
  })
})
