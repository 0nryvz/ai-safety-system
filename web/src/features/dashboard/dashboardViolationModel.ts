import type { RealtimeViolationRecord } from '../../core/realtime/realtimeViolationReducer'
import type { RecentViolation } from './dashboardTypes'

export interface DashboardViolation {
  violationId: string
  violationType: string
  cameraName: string | null
  departmentName: string | null
  occurredAt: string | null
  lifecycleStatus: string
  recordingStatus: string
  confidence: number | null
  source: 'REST' | 'REALTIME'
}

interface SortableDashboardViolation {
  violation: DashboardViolation
  sortAt: string | null
}

function getSortTime(value: string | null): number {
  if (!value) {
    return 0
  }

  const parsedTime = Date.parse(value)

  return Number.isNaN(parsedTime) ? 0 : parsedTime
}

function mapRecentViolation(violation: RecentViolation): SortableDashboardViolation {
  const occurredAt = violation.startedAt ?? violation.detectedAt

  return {
    violation: {
      violationId: violation.violationId,
      violationType: violation.violationType ?? 'UNKNOWN',
      cameraName: violation.cameraName,
      departmentName: null,
      occurredAt,
      lifecycleStatus: violation.lifecycleStatus ?? 'UNKNOWN',
      recordingStatus: violation.recordingStatus ?? 'UNKNOWN',
      confidence: violation.confidence,
      source: 'REST',
    },
    sortAt: occurredAt,
  }
}

function mapRealtimeViolation(violation: RealtimeViolationRecord): SortableDashboardViolation {
  return {
    violation: {
      violationId: violation.violationId,
      violationType: violation.type,
      cameraName: violation.cameraName,
      departmentName: violation.departmentName,
      occurredAt: violation.startedAt,
      lifecycleStatus: violation.lifecycleStatus,
      recordingStatus: violation.recordingStatus,
      confidence: violation.confidence,
      source: 'REALTIME',
    },
    sortAt: violation.lastEventAt,
  }
}

export function mergeDashboardViolations(
  recentViolations: RecentViolation[],
  realtimeViolations: RealtimeViolationRecord[],
): DashboardViolation[] {
  const violationsById = new Map<string, SortableDashboardViolation>()

  recentViolations.forEach((violation) => {
    violationsById.set(violation.violationId, mapRecentViolation(violation))
  })

  realtimeViolations.forEach((violation) => {
    if (violation.dismissed) {
      violationsById.delete(violation.violationId)
      return
    }

    violationsById.set(violation.violationId, mapRealtimeViolation(violation))
  })

  return [...violationsById.values()]
    .sort((left, right) => getSortTime(right.sortAt) - getSortTime(left.sortAt))
    .map(({ violation }) => violation)
}
