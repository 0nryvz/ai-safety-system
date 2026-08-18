export type CameraStatusVariant = 'neutral' | 'success' | 'warning' | 'critical'

export interface CameraStatusPresentation {
  label: string
  variant: CameraStatusVariant
}

export function getCameraStatusPresentation(status: string): CameraStatusPresentation {
  switch (status) {
    case 'ONLINE':
      return {
        label: 'Çevrimiçi',
        variant: 'success',
      }

    case 'WEAK':
    case 'DEGRADED':
      return {
        label: 'Zayıf',
        variant: 'warning',
      }

    case 'OFFLINE':
      return {
        label: 'Çevrim dışı',
        variant: 'critical',
      }

    default:
      return {
        label: 'Bilinmiyor',
        variant: 'neutral',
      }
  }
}
