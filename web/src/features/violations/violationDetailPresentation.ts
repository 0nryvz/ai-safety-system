import type {
  ViolationLifecycleStatus,
  ViolationRecordingStatus,
  ViolationReviewStatus,
  ViolationType,
} from '../../services/violationService'

export type ViolationDetailStatusVariant = 'neutral' | 'success' | 'warning' | 'critical' | 'info'

export interface ViolationDetailStatusPresentation {
  label: string
  variant: ViolationDetailStatusVariant
}

export function getViolationDetailTypeLabel(type: ViolationType): string {
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
  }
}

export function getViolationDetailLifecyclePresentation(
  status: ViolationLifecycleStatus,
): ViolationDetailStatusPresentation {
  switch (status) {
    case 'ACTIVE':
      return { label: 'Aktif', variant: 'critical' }
    case 'PREPARING':
      return { label: 'Hazırlanıyor', variant: 'warning' }
    case 'COMPLETED':
      return { label: 'Tamamlandı', variant: 'success' }
    case 'ERROR':
      return { label: 'Hata', variant: 'critical' }
  }
}

export function getViolationDetailReviewPresentation(
  status: ViolationReviewStatus,
): ViolationDetailStatusPresentation {
  switch (status) {
    case 'UNREVIEWED':
      return { label: 'İncelenmedi', variant: 'warning' }
    case 'REVIEWED':
      return { label: 'İncelendi', variant: 'info' }
    case 'CONFIRMED':
      return { label: 'Onaylandı', variant: 'success' }
    case 'FALSE_ALARM':
      return { label: 'Yanlış alarm', variant: 'neutral' }
  }
}

export function getViolationDetailRecordingPresentation(
  status: ViolationRecordingStatus,
): ViolationDetailStatusPresentation {
  switch (status) {
    case 'REQUESTED':
      return { label: 'Bekliyor', variant: 'neutral' }
    case 'RECORDING':
      return { label: 'Kaydediliyor', variant: 'info' }
    case 'PROCESSING':
      return { label: 'İşleniyor', variant: 'warning' }
    case 'READY':
      return { label: 'Hazır', variant: 'success' }
    case 'ERROR':
      return { label: 'Hata', variant: 'critical' }
  }
}
