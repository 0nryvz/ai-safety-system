import { describe, expect, it } from 'vitest'
import { ApiError } from '../../core/api/apiError'
import { getDashboardErrorContent } from './dashboardErrorContent'

describe('getDashboardErrorContent', () => {
  it.each([
    [0, 'Sunucuya bağlanılamadı'],
    [401, 'Oturum doğrulanamadı'],
    [403, 'Bu verilere erişim yetkiniz yok'],
    [400, 'İstek tamamlanamadı'],
    [500, 'Dashboard verileri yüklenemedi'],
    [-1, 'Beklenmeyen bir hata oluştu'],
  ])('maps status %s to the expected dashboard message', (status, expectedTitle) => {
    const content = getDashboardErrorContent(new ApiError('Test error', status))

    expect(content.title).toBe(expectedTitle)
    expect(content.description).not.toBe('')
  })
})
