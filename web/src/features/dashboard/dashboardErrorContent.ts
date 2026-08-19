import type { ApiError } from '../../core/api/apiError'
import { getApiErrorKind } from '../../core/api/apiErrorPolicy'

export interface DashboardErrorContent {
  title: string
  description: string
}

export function getDashboardErrorContent(error: ApiError): DashboardErrorContent {
  const errorKind = getApiErrorKind(error)

  switch (errorKind) {
    case 'network':
      return {
        title: 'Sunucuya bağlanılamadı',
        description: 'İnternet bağlantınızı kontrol edip yeniden deneyin.',
      }

    case 'unauthorized':
      return {
        title: 'Oturum doğrulanamadı',
        description: 'Devam etmek için yeniden giriş yapmanız gerekebilir.',
      }

    case 'forbidden':
      return {
        title: 'Bu verilere erişim yetkiniz yok',
        description: 'Dashboard verileri için gerekli yetkiye sahip değilsiniz.',
      }

    case 'client':
      return {
        title: 'İstek tamamlanamadı',
        description: 'Dashboard isteği geçersiz olduğu için tamamlanamadı.',
      }

    case 'server':
      return {
        title: 'Dashboard verileri yüklenemedi',
        description: 'Sunucuda bir sorun oluştu. Lütfen yeniden deneyin.',
      }

    case 'unknown':
      return {
        title: 'Beklenmeyen bir hata oluştu',
        description: 'Dashboard verileri yüklenirken beklenmeyen bir sorun oluştu.',
      }
  }
}
