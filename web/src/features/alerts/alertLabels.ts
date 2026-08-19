import type {
  RealtimeRecordingStatus,
  RealtimeViolationType,
} from '../../core/realtime/realtimeTypes'

const violationTypeLabels: Record<RealtimeViolationType, string> = {
  MISSING_WELDING_MASK: 'Kaynak maskesi eksik',
  MISSING_GLOVES: 'Koruyucu eldiven eksik',
  MISSING_WELDING_APRON: 'Kaynak önlüğü eksik',
  RESTRICTED_ZONE: 'Yasak bölge ihlali',
  UNPROTECTED_PERSON: 'Koruyucu ekipmansız personel',
  UNKNOWN: 'Bilinmeyen ihlal',
}

const recordingStatusLabels: Record<RealtimeRecordingStatus, string> = {
  REQUESTED: 'Kayıt hazırlanıyor',
  RECORDING: 'Kayıt devam ediyor',
  PROCESSING: 'Kayıt işleniyor',
  READY: 'Kayıt hazır',
  ERROR: 'Kayıt hatası',
  UNKNOWN: 'Kayıt durumu bilinmiyor',
}

export function getViolationTypeLabel(type: RealtimeViolationType): string {
  return violationTypeLabels[type]
}

export function getRecordingStatusLabel(status: RealtimeRecordingStatus): string {
  return recordingStatusLabels[status]
}

export function formatConfidence(confidence: number): string {
  const normalizedConfidence = Math.min(Math.max(confidence, 0), 1)

  return `%${Math.round(normalizedConfidence * 100)}`
}
