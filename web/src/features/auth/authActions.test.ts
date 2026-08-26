import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authService from '../../services/authService'
import * as userService from '../../services/userService'
import { authTokenProvider, clearSession, setAuthenticatedSession } from './authTokenProvider'
import { hydrateCurrentUser, performLogout } from './authActions'
import type { AuthSession } from './sessionTypes'

const session: AuthSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
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

describe('hydrateCurrentUser', () => {
  it('loads the current user and updates the authenticated session', async () => {
    setAuthenticatedSession(session)

    vi.spyOn(userService, 'getCurrentUser').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      fullName: 'Test User',
      active: true,
      departmentId: null,
      departmentName: null,
      roles: ['OHS_SPECIALIST'],
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      createdAt: '2026-08-15T00:00:00Z',
    })

    const user = await hydrateCurrentUser()

    expect(user.roles).toEqual(['OHS_SPECIALIST'])
    expect(authTokenProvider.getSession()?.user).toEqual(user)
    expect(authTokenProvider.getAccessToken()).toBe('access-token')
  })

  it('fails without requesting the profile when no session exists', async () => {
    const getCurrentUserSpy = vi.spyOn(userService, 'getCurrentUser')

    await expect(hydrateCurrentUser()).rejects.toThrow('Authenticated session is not available.')

    expect(getCurrentUserSpy).not.toHaveBeenCalled()
  })
})

describe('performLogout', () => {
  it('calls the backend logout endpoint and clears the local session', async () => {
    setAuthenticatedSession(session)

    const logoutSpy = vi.spyOn(authService, 'logout').mockResolvedValue()

    await performLogout()

    expect(logoutSpy).toHaveBeenCalledWith('refresh-token')
    expect(authTokenProvider.getStatus()).toBe('anonymous')
    expect(authTokenProvider.getSession()).toBeNull()
    expect(window.sessionStorage.getItem('authSession')).toBeNull()
  })

  it('still clears the local session when backend logout fails', async () => {
    setAuthenticatedSession(session)

    vi.spyOn(authService, 'logout').mockRejectedValue(new Error('Backend logout failed'))

    await expect(performLogout()).rejects.toThrow('Backend logout failed')

    expect(authTokenProvider.getStatus()).toBe('anonymous')
    expect(authTokenProvider.getSession()).toBeNull()
    expect(window.sessionStorage.getItem('authSession')).toBeNull()
  })

  it('clears locally without calling the backend when no refresh token exists', async () => {
    const logoutSpy = vi.spyOn(authService, 'logout')

    await performLogout()

    expect(logoutSpy).not.toHaveBeenCalled()
    expect(authTokenProvider.getStatus()).toBe('anonymous')
  })
})
