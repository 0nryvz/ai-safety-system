export type RealtimeConnectionStatus = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'OFFLINE'

export interface RealtimeMessage {
  body: string
  headers: Record<string, string>
}

export type RealtimeMessageHandler = (message: RealtimeMessage) => void

export type RealtimeConnectionListener = (status: RealtimeConnectionStatus) => void

export type RealtimeRecoveryCallback = () => void | Promise<void>

export const realtimeViolationTypes = [
  'MISSING_WELDING_MASK',
  'MISSING_GLOVES',
  'MISSING_WELDING_APRON',
  'RESTRICTED_ZONE',
  'UNPROTECTED_PERSON',
] as const

export const realtimeLifecycleStatuses = ['ACTIVE', 'PREPARING', 'COMPLETED', 'ERROR'] as const

export const realtimeRecordingStatuses = [
  'REQUESTED',
  'RECORDING',
  'PROCESSING',
  'READY',
  'ERROR',
] as const

export type RealtimeViolationType = (typeof realtimeViolationTypes)[number] | 'UNKNOWN'

export type RealtimeLifecycleStatus = (typeof realtimeLifecycleStatuses)[number] | 'UNKNOWN'

export type RealtimeRecordingStatus = (typeof realtimeRecordingStatuses)[number] | 'UNKNOWN'
export interface RealtimeAlertMessage {
  violationId: string
  type: RealtimeViolationType
  cameraName: string
  departmentName: string
  startedAt: string
  confidence: number
  lifecycleStatus: RealtimeLifecycleStatus
  recordingStatus: RealtimeRecordingStatus
  clipReady: boolean
  coverImageReady: boolean
}

export interface RealtimeViolationUpdateMessage {
  violationId: string
  lifecycleStatus: RealtimeLifecycleStatus
  recordingStatus: RealtimeRecordingStatus
  clipReady: boolean
  updatedAt: string
  errorCode?: string | null
}

export type RealtimeAlertPayload = RealtimeAlertMessage | RealtimeViolationUpdateMessage
