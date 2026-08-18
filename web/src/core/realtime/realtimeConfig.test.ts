import { describe, expect, it } from 'vitest'
import { ALERTS_DESTINATION, REALTIME_ENDPOINT, resolveWebSocketUrl } from './realtimeConfig'

describe('realtimeConfig', () => {
  it('keeps an explicit WebSocket URL unchanged', () => {
    expect(resolveWebSocketUrl('ws://localhost:8080/ws')).toBe('ws://localhost:8080/ws')
  })

  it('converts an HTTPS URL to WSS', () => {
    expect(resolveWebSocketUrl('https://example.com/ws')).toBe('wss://example.com/ws')
  })

  it('uses the current host and secure protocol for a relative URL', () => {
    expect(
      resolveWebSocketUrl('/ws', {
        protocol: 'https:',
        host: 'safety.example.com',
      }),
    ).toBe('wss://safety.example.com/ws')
  })

  it('uses the default endpoint when configuration is empty', () => {
    expect(
      resolveWebSocketUrl('', {
        protocol: 'http:',
        host: 'localhost:5173',
      }),
    ).toBe('ws://localhost:5173/ws')
  })

  it('exposes the backend endpoint and destination contracts', () => {
    expect(REALTIME_ENDPOINT).toBe('/ws')
    expect(ALERTS_DESTINATION).toBe('/user/queue/alerts')
  })
})
