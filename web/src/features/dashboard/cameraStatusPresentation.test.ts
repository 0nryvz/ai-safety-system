import { describe, expect, it } from 'vitest'
import { getCameraStatusPresentation } from './cameraStatusPresentation'

describe('getCameraStatusPresentation', () => {
  it.each([
    ['ONLINE', 'Çevrimiçi', 'success'],
    ['WEAK', 'Zayıf', 'warning'],
    ['DEGRADED', 'Zayıf', 'warning'],
    ['OFFLINE', 'Çevrim dışı', 'critical'],
    ['FUTURE_STATUS', 'Bilinmiyor', 'neutral'],
    [null, 'Bilinmiyor', 'neutral'],
    [undefined, 'Bilinmiyor', 'neutral'],
  ] as const)('maps %s to a safe presentation', (status, label, variant) => {
    expect(getCameraStatusPresentation(status)).toEqual({
      label,
      variant,
    })
  })
})
