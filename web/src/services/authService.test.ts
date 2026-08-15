import { afterEach, describe, expect, it, vi } from 'vitest'
import { publicApiClient } from '../core/api/publicApiClient'
import { login, logout, refreshAuthTokens, type AuthResponse } from './authService'

const authResponse: AuthResponse = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('authService', () => {
  it('logs in with the confirmed backend contract', async () => {
    const postSpy = vi.spyOn(publicApiClient, 'post').mockResolvedValue({
      data: authResponse,
    })

    const result = await login({
      email: 'user@example.com',
      password: 'password',
    })

    expect(postSpy).toHaveBeenCalledWith('/auth/login', {
      email: 'user@example.com',
      password: 'password',
    })

    expect(result).toEqual(authResponse)
  })

  it('refreshes tokens using the refresh token', async () => {
    const postSpy = vi.spyOn(publicApiClient, 'post').mockResolvedValue({
      data: authResponse,
    })

    const result = await refreshAuthTokens('old-refresh-token')

    expect(postSpy).toHaveBeenCalledWith('/auth/refresh', {
      refreshToken: 'old-refresh-token',
    })

    expect(result).toEqual(authResponse)
  })

  it('logs out using the refresh token', async () => {
    const postSpy = vi.spyOn(publicApiClient, 'post').mockResolvedValue({
      data: undefined,
    })

    await logout('refresh-token')

    expect(postSpy).toHaveBeenCalledWith('/auth/logout', {
      refreshToken: 'refresh-token',
    })
  })
})
