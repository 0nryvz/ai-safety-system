import type { ApiError } from './apiError'

export type ApiErrorKind =
  'network' | 'unauthorized' | 'forbidden' | 'client' | 'server' | 'unknown'

export function getApiErrorKind(error: ApiError): ApiErrorKind {
  if (error.status === 0) {
    return 'network'
  }

  if (error.status === 401) {
    return 'unauthorized'
  }

  if (error.status === 403) {
    return 'forbidden'
  }

  if (error.status >= 400 && error.status < 500) {
    return 'client'
  }

  if (error.status >= 500) {
    return 'server'
  }

  return 'unknown'
}
