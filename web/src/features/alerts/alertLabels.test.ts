import { describe, expect, it } from 'vitest'
import { formatConfidence, getRecordingStatusLabel, getViolationTypeLabel } from './alertLabels'

describe('alertLabels', () => {
  it('returns user-friendly violation labels', () => {
    expect(getViolationTypeLabel('MISSING_GLOVES')).toBe('Koruyucu eldiven eksik')
    expect(getViolationTypeLabel('UNKNOWN')).toBe('Bilinmeyen ihlal')
  })

  it('returns user-friendly recording status labels', () => {
    expect(getRecordingStatusLabel('REQUESTED')).toBe('Kayıt hazırlanıyor')
    expect(getRecordingStatusLabel('READY')).toBe('Kayıt hazır')
  })

  it('formats and limits confidence values', () => {
    expect(formatConfidence(0.943)).toBe('%94')
    expect(formatConfidence(2)).toBe('%100')
    expect(formatConfidence(-1)).toBe('%0')
  })
})
