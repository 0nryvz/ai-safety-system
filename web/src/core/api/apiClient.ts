import axios from 'axios'
import { env } from '../../config/env'
import { mapApiError } from './apiErrorMapper'

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(mapApiError(error)),
)
