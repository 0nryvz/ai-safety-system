import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  authTokenProvider,
  clearSession,
  initializeAuthSession,
  setAuthenticatedSession,
  updateSessionTokens,
  updateSessionUser,
} from './authTokenProvider'
import type { AuthSession, AuthUser } from './sessionTypes'

const session: AuthSession = {
  accessToken: 'access-token-1',
  refreshToken: 'refresh-token-1',
  tokenType: 'Bearer',
  user: null,
}

beforeEach(() => {
  window.sessionStorage.clear()
  clearSession('logout')
})

afterEach(() => {
  window.sessionStorage.clear()
  clearSession('logout')
})

describe('authTokenProvider', () => {
  it('exposes and persists the authenticated session', () => {
    setAuthenticatedSession(session)

    expect(authTokenProvider.getStatus()).toBe('authenticated')
    expect(authTokenProvider.getAccessToken()).toBe('access-token-1')
    expect(authTokenProvider.getSession()).toEqual(session)

    expect(JSON.parse(window.sessionStorage.getItem('authSession') ?? '')).toEqual(session)
  })

  it('restores a stored session during initialization', () => {
    window.sessionStorage.setItem('authSession', JSON.stringify(session))

    initializeAuthSession()

    expect(authTokenProvider.getStatus()).toBe('authenticated')
    expect(authTokenProvider.getAccessToken()).toBe('access-token-1')
    expect(authTokenProvider.getSession()).toEqual(session)
  })

  it('initializes as anonymous when no stored session exists', () => {
    initializeAuthSession()

    expect(authTokenProvider.getStatus()).toBe('anonymous')
    expect(authTokenProvider.getAccessToken()).toBeNull()
    expect(authTokenProvider.getSession()).toBeNull()
  })

  it('notifies subscribers when login occurs', () => {
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
  })

  it('persists refreshed tokens and notifies subscribers', () => {
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

    expect(JSON.parse(window.sessionStorage.getItem('authSession') ?? '')).toEqual(refreshedSession)

    expect(listener).toHaveBeenCalledWith(
      {
        status: 'authenticated',
        session: refreshedSession,
      },
      'token-refresh',
    )

    unsubscribe()
  })

  it('updates only the user profile and notifies subscribers', () => {
    setAuthenticatedSession(session)

    const listener = vi.fn()
    const unsubscribe = authTokenProvider.subscribe(listener)

    const user: AuthUser = {
      id: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      fullName: 'Test User',
      active: true,
      roles: ['OHS_SPECIALIST'],
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
    }

    updateSessionUser(user)

    const updatedSession = authTokenProvider.getSession()

    expect(updatedSession).toEqual({
      ...session,
      user,
    })

    expect(authTokenProvider.getAccessToken()).toBe('access-token-1')

    expect(JSON.parse(window.sessionStorage.getItem('authSession') ?? '')).toEqual({
      ...session,
      user,
    })

    expect(listener).toHaveBeenCalledWith(
      {
        status: 'authenticated',
        session: {
          ...session,
          user,
        },
      },
      'profile-update',
    )

    unsubscribe()
  })

  it('clears persisted session on logout', () => {
    setAuthenticatedSession(session)

    clearSession('logout')

    expect(authTokenProvider.getStatus()).toBe('anonymous')
    expect(authTokenProvider.getSession()).toBeNull()
    expect(window.sessionStorage.getItem('authSession')).toBeNull()
  })

  it('clears persisted session and marks expiry', () => {
    setAuthenticatedSession(session)

    clearSession('expiry')

    expect(authTokenProvider.getStatus()).toBe('expired')
    expect(authTokenProvider.getAccessToken()).toBeNull()
    expect(authTokenProvider.getSession()).toBeNull()
    expect(window.sessionStorage.getItem('authSession')).toBeNull()
  })
})
