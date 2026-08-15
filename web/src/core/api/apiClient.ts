import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { env } from '../../config/env'
import { refreshSessionSingleFlight } from '../../features/auth/authRefresh'
import { authTokenProvider } from '../../features/auth/authTokenProvider'
import { mapApiError } from './apiErrorMapper'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const accessToken = authTokenProvider.getAccessToken()

  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryableRequestConfig | undefined
    const status = error.response?.status

    if (status !== 401 || !config || config._retry) {
      return Promise.reject(mapApiError(error))
    }

    config._retry = true

    try {
      const refreshedSession = await refreshSessionSingleFlight()

      config.headers.set('Authorization', `Bearer ${refreshedSession.accessToken}`)

      return apiClient.request(config)
    } catch {
      return Promise.reject(mapApiError(error))
    }
  },
)
