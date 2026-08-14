import { describe, expect, it } from 'vitest'
import { env } from '../../config/env'
import { apiClient } from './apiClient'

describe('apiClient', () => {
  it('uses the centralized API base URL', () => {
    expect(apiClient.defaults.baseURL).toBe(env.apiBaseUrl)
  })

  it('configures JSON requests', () => {
    expect(apiClient.defaults.headers['Content-Type']).toBe('application/json')
  })
})
