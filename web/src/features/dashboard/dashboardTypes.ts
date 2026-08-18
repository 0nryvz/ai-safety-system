export interface DashboardSummary {
  todayViolationCount: number
  last7DaysViolationCount: number
  mostFrequentViolationType: string | null
  activeCameraCount: number
  offlineCameraCount: number
  activeViolationCount: number
}

export interface RecentViolation {
  violationId: string
  detectedAt: string | null
  startedAt: string | null
  violationType: string | null
  cameraId: string | null
  departmentId: string | null
  cameraName: string | null
  cameraCode: string | null
  lifecycleStatus: string | null
  reviewStatus: string | null
  recordingStatus: string | null
  recordingReadyAt: string | null
  recordingObjectKey: string | null
  coverImageKey: string | null
  confidence: number | null
  modelVersion: string | null
}

export interface Camera {
  id: string
  name: string
  code: string
  departmentId: string
  active: boolean
  connectionStatus: string
  lastSeenAt: string | null
  activeSessionId: string | null
}
