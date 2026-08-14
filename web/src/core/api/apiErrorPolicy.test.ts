import { describe, expect, it } from 'vitest'
import { ApiError } from './apiError'
import { getApiErrorKind } from './apiErrorPolicy'

describe('getApiErrorKind', () => {
  it('classifies network errors', () => {
    expect(getApiErrorKind(new ApiError('Network error', 0))).toBe('network')
  })

  it('separates 401 from 403', () => {
    expect(getApiErrorKind(new ApiError('Unauthorized', 401))).toBe('unauthorized')
    expect(getApiErrorKind(new ApiError('Forbidden', 403))).toBe('forbidden')
  })

  it('classifies other client and server errors', () => {
    expect(getApiErrorKind(new ApiError('Bad request', 400))).toBe('client')
    expect(getApiErrorKind(new ApiError('Conflict', 409))).toBe('client')
    expect(getApiErrorKind(new ApiError('Server error', 500))).toBe('server')
  })
})
