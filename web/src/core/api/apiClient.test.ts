import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { env } from '../../config/env'
import * as authRefresh from '../../features/auth/authRefresh'
import {
  clearSession,
  setAuthenticatedSession,
  updateSessionTokens,
} from '../../features/auth/authTokenProvider'
import type { AuthSession } from '../../features/auth/sessionTypes'
import { apiClient } from './apiClient'

const session: AuthSession = {
  accessToken: 'test-access-token',
  refreshToken: 'test-refresh-token',
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

describe('apiClient', () => {
  it('uses the centralized API base URL', () => {
    expect(apiClient.defaults.baseURL).toBe(env.apiBaseUrl)
  })

  it('configures JSON requests', () => {
    expect(apiClient.defaults.headers['Content-Type']).toBe('application/json')
  })

  it('adds the Bearer token to authenticated requests', async () => {
    setAuthenticatedSession(session)

    const response = await apiClient.get('/example', {
      adapter: async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => ({
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }),
    })

    expect(response.config.headers.Authorization).toBe('Bearer test-access-token')
  })

  it('does not add Authorization when there is no session', async () => {
    const response = await apiClient.get('/example', {
      adapter: async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => ({
        data: {},
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }),
    })

    expect(response.config.headers.Authorization).toBeUndefined()
  })

  it('refreshes after a 401 and retries the request once', async () => {
    setAuthenticatedSession(session)

    vi.spyOn(authRefresh, 'refreshSessionSingleFlight').mockImplementation(async () => {
      const refreshedSession: AuthSession = {
        ...session,
        accessToken: 'refreshed-access-token',
        refreshToken: 'refreshed-refresh-token',
      }

      updateSessionTokens(refreshedSession)

      return refreshedSession
    })

    let requestCount = 0

    const response = await apiClient.get('/protected', {
      adapter: async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
        requestCount += 1

        if (requestCount === 1) {
          throw new axios.AxiosError('Unauthorized', undefined, config, undefined, {
            data: {},
            status: 401,
            statusText: 'Unauthorized',
            headers: {},
            config,
          })
        }

        return {
          data: {},
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        }
      },
    })

    expect(requestCount).toBe(2)
    expect(response.status).toBe(200)
    expect(response.config.headers.Authorization).toBe('Bearer refreshed-access-token')
  })
})
