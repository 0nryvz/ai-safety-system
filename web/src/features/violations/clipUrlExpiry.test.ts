import { describe, expect, it } from 'vitest'
import { getClipUrlRefreshDelay } from './clipUrlExpiry'

describe('getClipUrlRefreshDelay', () => {
  const now = Date.parse('2026-08-18T10:00:00Z')

  it('refreshes a normal URL thirty seconds before expiry', () => {
    expect(getClipUrlRefreshDelay('2026-08-18T10:05:00Z', now)).toBe(270_000)
  })

  it('refreshes a short-lived URL halfway through its remaining time', () => {
    expect(getClipUrlRefreshDelay('2026-08-18T10:00:20Z', now)).toBe(10_000)
  })

  it('does not schedule an already expired URL', () => {
    expect(getClipUrlRefreshDelay('2026-08-18T09:59:59Z', now)).toBeNull()
  })

  it('does not schedule an invalid expiry value', () => {
    expect(getClipUrlRefreshDelay('invalid-date', now)).toBeNull()
  })

  it('caps refresh timers that exceed the browser timeout limit', () => {
    expect(getClipUrlRefreshDelay('2099-08-18T10:05:00Z', now)).toBe(2_147_483_647)
  })
})
