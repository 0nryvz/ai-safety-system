import { describe, expect, it } from 'vitest'
import { RealtimeSeenEventCache } from './RealtimeSeenEventCache'

describe('RealtimeSeenEventCache', () => {
  it('marks the second occurrence of an event as duplicate', () => {
    const cache = new RealtimeSeenEventCache({
      ttlMs: 1_000,
      maxEntries: 10,
    })

    expect(cache.checkAndRemember('event-1', 0)).toBe(false)
    expect(cache.checkAndRemember('event-1', 500)).toBe(true)
  })

  it('accepts the same event again after its TTL expires', () => {
    const cache = new RealtimeSeenEventCache({
      ttlMs: 1_000,
      maxEntries: 10,
    })

    expect(cache.checkAndRemember('event-1', 0)).toBe(false)
    expect(cache.checkAndRemember('event-1', 1_001)).toBe(false)
  })

  it('evicts the oldest event when maximum capacity is exceeded', () => {
    const cache = new RealtimeSeenEventCache({
      ttlMs: 10_000,
      maxEntries: 2,
    })

    expect(cache.checkAndRemember('event-1', 0)).toBe(false)
    expect(cache.checkAndRemember('event-2', 1)).toBe(false)
    expect(cache.checkAndRemember('event-3', 2)).toBe(false)

    expect(cache.checkAndRemember('event-1', 3)).toBe(false)
  })

  it('can be cleared explicitly', () => {
    const cache = new RealtimeSeenEventCache({
      ttlMs: 1_000,
      maxEntries: 10,
    })

    cache.checkAndRemember('event-1', 0)
    cache.clear()

    expect(cache.checkAndRemember('event-1', 1)).toBe(false)
  })
})
