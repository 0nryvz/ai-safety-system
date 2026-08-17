import axios from 'axios'
import { describe, expect, it } from 'vitest'
import { mapApiError } from './apiErrorMapper'

describe('mapApiError', () => {
  it('maps a backend response error', () => {
    const error = new axios.AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
      data: {
        timestamp: '2026-08-15T00:00:00Z',
        status: 400,
        error: 'Bad Request',
        message: 'Geçersiz istek.',
        path: '/api/v1/example',
      },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: {
        headers: new axios.AxiosHeaders(),
      },
    })

    const mappedError = mapApiError(error)

    expect(mappedError.status).toBe(400)
    expect(mappedError.message).toBe('Geçersiz istek.')
    expect(mappedError.response?.path).toBe('/api/v1/example')
  })

  it('maps an axios error without a response as a network error', () => {
    const error = new axios.AxiosError('Network Error')

    const mappedError = mapApiError(error)

    expect(mappedError.status).toBe(0)
  })

  it('maps an unknown error safely', () => {
    const mappedError = mapApiError(new Error('Unknown'))

    expect(mappedError.status).toBe(0)
  })
})
