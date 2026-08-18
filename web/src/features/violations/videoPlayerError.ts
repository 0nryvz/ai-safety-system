import { ApiError } from '../../core/api/apiError'

export interface VideoPlayerErrorMessage {
  title: string
  description: string
  canRetry: boolean
}

export function getVideoPlayerErrorMessage(error: unknown): VideoPlayerErrorMessage {
  if (!(error instanceof ApiError)) {
    return {
      title: 'Video yüklenemedi',
      description: 'Video alınırken beklenmeyen bir hata oluştu.',
      canRetry: true,
    }
  }

  switch (error.status) {
    case 403:
      return {
        title: 'Videoya erişim izniniz yok',
        description: 'Bu ihlal videosunu görüntülemek için gerekli yetkiye sahip değilsiniz.',
        canRetry: false,
      }

    case 409:
      return {
        title: 'Video henüz hazır değil',
        description: 'Video hazırlanmaya devam ediyor. Bir süre sonra tekrar deneyebilirsiniz.',
        canRetry: true,
      }

    case 404:
      return {
        title: 'Video bulunamadı',
        description: 'Bu ihlale ait video kaydı bulunamadı.',
        canRetry: true,
      }

    case 0:
      return {
        title: 'Sunucuya ulaşılamadı',
        description: 'Bağlantınızı kontrol edip video isteğini tekrar deneyebilirsiniz.',
        canRetry: true,
      }

    default:
      return {
        title: 'Video yüklenemedi',
        description: error.message,
        canRetry: true,
      }
  }
}
