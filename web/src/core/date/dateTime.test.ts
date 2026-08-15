import { describe, expect, it } from 'vitest'
import { localDateToUtcIso, parseUtcDate } from './dateTime'

describe('dateTime helpers', () => {
  it('parses a UTC ISO timestamp', () => {
    const date = parseUtcDate('2026-08-14T22:00:00Z')

    expect(date.toISOString()).toBe('2026-08-14T22:00:00.000Z')
  })

  it('converts a Date value to UTC ISO format', () => {
    const date = new Date('2026-08-15T01:00:00+03:00')

    expect(localDateToUtcIso(date)).toBe('2026-08-14T22:00:00.000Z')
  })
})
