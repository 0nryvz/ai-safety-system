import axios from 'axios'
import { ApiError, type ApiErrorResponse } from './apiError'

const DEFAULT_ERROR_MESSAGE = 'İstek işlenirken bir hata oluştu.'
const NETWORK_ERROR_MESSAGE = 'Sunucuya bağlanılamadı.'

export function mapApiError(error: unknown): ApiError {
  if (!axios.isAxiosError<ApiErrorResponse>(error)) {
    return new ApiError(DEFAULT_ERROR_MESSAGE, 0)
  }

  if (!error.response) {
    return new ApiError(NETWORK_ERROR_MESSAGE, 0)
  }

  const { status, data } = error.response
  const message = data?.message || DEFAULT_ERROR_MESSAGE

  return new ApiError(message, status, data)
}
