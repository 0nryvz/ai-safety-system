import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authActions from './authActions'
import { authTokenProvider, clearSession } from './authTokenProvider'
import { bootstrapAuthSession } from './authBootstrap'
import type { AuthSession } from './sessionTypes'

const storedSession: AuthSession = {
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

describe('bootstrapAuthSession', () => {
  it('restores the stored session and hydrates the current user', async () => {
    window.sessionStorage.setItem('authSession', JSON.stringify(storedSession))

    const hydrateSpy = vi.spyOn(authActions, 'hydrateCurrentUser').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      fullName: 'Test User',
      active: true,
      roles: ['OHS_SPECIALIST'],
      departmentIds: [],
    })

    await bootstrapAuthSession()

    expect(authTokenProvider.getStatus()).toBe('authenticated')
    expect(authTokenProvider.getAccessToken()).toBe('access-token')
    expect(hydrateSpy).toHaveBeenCalledTimes(1)
  })

  it('does not hydrate when there is no stored session', async () => {
    const hydrateSpy = vi.spyOn(authActions, 'hydrateCurrentUser')

    await bootstrapAuthSession()

    expect(authTokenProvider.getStatus()).toBe('anonymous')
    expect(hydrateSpy).not.toHaveBeenCalled()
  })
  it('clears the restored session when hydration fails', async () => {
    window.sessionStorage.setItem('authSession', JSON.stringify(storedSession))

    vi.spyOn(authActions, 'hydrateCurrentUser').mockRejectedValue(
      new Error('Profile hydration failed'),
    )

    await bootstrapAuthSession()

    expect(authTokenProvider.getStatus()).toBe('expired')
    expect(authTokenProvider.getSession()).toBeNull()
    expect(window.sessionStorage.getItem('authSession')).toBeNull()
  })
})
