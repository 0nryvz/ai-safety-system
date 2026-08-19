import { describe, expect, it } from 'vitest'
import {
  getLifecycleStatusPresentation,
  getRecordingStatusPresentation,
  getViolationTypeLabel,
} from './violationPresentation'

describe('violationPresentation', () => {
  it('maps known violation types to readable labels', () => {
    expect(getViolationTypeLabel('MISSING_GLOVES')).toBe('Eldiven eksik')
    expect(getViolationTypeLabel('RESTRICTED_ZONE')).toBe('Yasak bölge ihlali')
  })

  it('uses a safe label for an unknown violation type', () => {
    expect(getViolationTypeLabel('FUTURE_TYPE')).toBe('Bilinmeyen ihlal')
  })

  it.each([
    ['ACTIVE', 'Aktif', 'critical'],
    ['PREPARING', 'Hazırlanıyor', 'warning'],
    ['COMPLETED', 'Tamamlandı', 'success'],
    ['ERROR', 'Hata', 'critical'],
    ['FUTURE_STATUS', 'Bilinmiyor', 'neutral'],
  ] as const)('maps lifecycle status %s safely', (status, label, variant) => {
    expect(getLifecycleStatusPresentation(status)).toEqual({
      label,
      variant,
    })
  })

  it.each([
    ['REQUESTED', 'Bekliyor', 'neutral'],
    ['PENDING', 'Bekliyor', 'neutral'],
    ['RECORDING', 'Kaydediliyor', 'info'],
    ['PROCESSING', 'İşleniyor', 'warning'],
    ['READY', 'Hazır', 'success'],
    ['ERROR', 'Hata', 'critical'],
    ['FUTURE_STATUS', 'Bilinmiyor', 'neutral'],
  ] as const)('maps recording status %s safely', (status, label, variant) => {
    expect(getRecordingStatusPresentation(status)).toEqual({
      label,
      variant,
    })
  })
})
