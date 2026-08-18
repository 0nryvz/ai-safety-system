import type { RealtimeRecordingStatus } from '../../core/realtime/realtimeTypes'

export type VideoPlayerViewState =
  | {
      kind: 'preparing'
      title: string
      description: string
    }
  | {
      kind: 'ready'
    }
  | {
      kind: 'error'
      title: string
      description: string
    }

export function getVideoPlayerViewState(
  recordingStatus: RealtimeRecordingStatus,
): VideoPlayerViewState {
  switch (recordingStatus) {
    case 'REQUESTED':
      return {
        kind: 'preparing',
        title: 'Video kaydı bekleniyor',
        description: 'Kayıt isteği alındı. Video hazırlanırken lütfen bekleyin.',
      }

    case 'RECORDING':
      return {
        kind: 'preparing',
        title: 'Video kaydediliyor',
        description: 'İhlal videosunun kaydı devam ediyor.',
      }

    case 'PROCESSING':
      return {
        kind: 'preparing',
        title: 'Video işleniyor',
        description: 'Video oynatılmaya hazır hale getiriliyor.',
      }

    case 'READY':
      return {
        kind: 'ready',
      }

    case 'ERROR':
      return {
        kind: 'error',
        title: 'Video hazırlanamadı',
        description: 'İhlal videosu oluşturulurken bir hata meydana geldi.',
      }

    case 'UNKNOWN':
      return {
        kind: 'error',
        title: 'Video durumu bilinmiyor',
        description: 'Video kayıt durumu şu anda doğrulanamıyor.',
      }
  }
}
