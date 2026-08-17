export type RealtimeConnectionStatus = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'OFFLINE'

export interface RealtimeMessage {
  body: string
  headers: Record<string, string>
}

export type RealtimeMessageHandler = (message: RealtimeMessage) => void

export type RealtimeConnectionListener = (status: RealtimeConnectionStatus) => void

export type RealtimeRecoveryCallback = () => void | Promise<void>

export interface RealtimeAlertMessage {
  violationId: string
  type: string
  cameraName: string
  departmentName: string
  startedAt: string
  confidence: number
  lifecycleStatus: string
  recordingStatus: string
  clipReady: boolean
  coverImageReady: boolean
}

export interface RealtimeViolationUpdateMessage {
  violationId: string
  lifecycleStatus: string
  recordingStatus: string
  clipReady: boolean
  updatedAt: string
  errorCode?: string | null
}

export type RealtimeAlertPayload = RealtimeAlertMessage | RealtimeViolationUpdateMessage
