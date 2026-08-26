export type ViolationStatusVariant = 'neutral' | 'success' | 'warning' | 'critical' | 'info'

export interface ViolationStatusPresentation {
  label: string
  variant: ViolationStatusVariant
}

export function getViolationTypeLabel(type: string): string {
  switch (type) {
    case 'MISSING_WELDING_MASK':
      return 'Kaynak maskesi eksik'

    case 'MISSING_GLOVES':
      return 'Eldiven eksik'

    case 'MISSING_WELDING_APRON':
      return 'Kaynak önlüğü eksik'

    case 'RESTRICTED_ZONE':
      return 'Yasak bölge ihlali'

    case 'UNPROTECTED_PERSON':
      return 'Koruyucu ekipmansız kişi'

    default:
      return 'Bilinmeyen ihlal'
  }
}

export function getLifecycleStatusPresentation(status: string): ViolationStatusPresentation {
  switch (status) {
    case 'ACTIVE':
      return {
        label: 'Aktif',
        variant: 'critical',
      }

    case 'PREPARING':
      return {
        label: 'Hazırlanıyor',
        variant: 'warning',
      }

    case 'COMPLETED':
      return {
        label: 'Tamamlandı',
        variant: 'success',
      }

    case 'ERROR':
      return {
        label: 'Hata',
        variant: 'critical',
      }

    default:
      return {
        label: 'Bilinmiyor',
        variant: 'neutral',
      }
  }
}

export function getRecordingStatusPresentation(status: string): ViolationStatusPresentation {
  switch (status) {
    case 'REQUESTED':
    case 'PENDING':
      return {
        label: 'Bekliyor',
        variant: 'neutral',
      }

    case 'RECORDING':
      return {
        label: 'Kaydediliyor',
        variant: 'info',
      }

    case 'PROCESSING':
      return {
        label: 'İşleniyor',
        variant: 'warning',
      }

    case 'READY':
      return {
        label: 'Hazır',
        variant: 'success',
      }

    case 'ERROR':
      return {
        label: 'Hata',
        variant: 'critical',
      }

    default:
      return {
        label: 'Bilinmiyor',
        variant: 'neutral',
      }
  }
}
