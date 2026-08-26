import {
  realtimeLifecycleStatuses,
  realtimeRecordingStatuses,
  realtimeViolationTypes,
  type RealtimeAlertMessage,
  type RealtimeLifecycleStatus,
  type RealtimeRecordingStatus,
  type RealtimeViolationType,
  type RealtimeViolationUpdateMessage,
} from './realtimeTypes'

export type ParsedRealtimePayload =
  | {
      kind: 'ALERT'
      payload: RealtimeAlertMessage
    }
  | {
      kind: 'VIOLATION_UPDATE'
      payload: RealtimeViolationUpdateMessage
    }

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isValidInstant(value: unknown): value is string {
  return isNonEmptyString(value) && !Number.isNaN(Date.parse(value))
}

function normalizeKnownValue<T extends string>(
  value: unknown,
  knownValues: readonly T[],
): T | 'UNKNOWN' | null {
  if (!isNonEmptyString(value)) {
    return null
  }

  return knownValues.includes(value as T) ? (value as T) : 'UNKNOWN'
}

function normalizeViolationType(value: unknown): RealtimeViolationType | null {
  return normalizeKnownValue(value, realtimeViolationTypes)
}

function normalizeLifecycleStatus(value: unknown): RealtimeLifecycleStatus | null {
  return normalizeKnownValue(value, realtimeLifecycleStatuses)
}

function normalizeRecordingStatus(value: unknown): RealtimeRecordingStatus | null {
  return normalizeKnownValue(value, realtimeRecordingStatuses)
}

function parseAlertPayload(payload: Record<string, unknown>): RealtimeAlertMessage | null {
  const type = normalizeViolationType(payload.type)
  const lifecycleStatus = normalizeLifecycleStatus(payload.lifecycleStatus)
  const recordingStatus = normalizeRecordingStatus(payload.recordingStatus)

  if (
    !isNonEmptyString(payload.eventId) ||
    typeof payload.version !== 'number' ||
    !Number.isInteger(payload.version) ||
    payload.version < 0 ||
    !isNonEmptyString(payload.violationId) ||
    type === null ||
    !isNonEmptyString(payload.cameraName) ||
    !isNonEmptyString(payload.departmentName) ||
    !isValidInstant(payload.startedAt) ||
    typeof payload.confidence !== 'number' ||
    !Number.isFinite(payload.confidence) ||
    payload.confidence < 0 ||
    payload.confidence > 1 ||
    lifecycleStatus === null ||
    recordingStatus === null ||
    typeof payload.clipReady !== 'boolean' ||
    typeof payload.coverImageReady !== 'boolean'
  ) {
    return null
  }

  return {
    eventId: payload.eventId,
    version: payload.version,
    violationId: payload.violationId,
    type,
    cameraName: payload.cameraName,
    departmentName: payload.departmentName,
    startedAt: payload.startedAt,
    confidence: payload.confidence,
    lifecycleStatus,
    recordingStatus,
    clipReady: payload.clipReady,
    coverImageReady: payload.coverImageReady,
  }
}

function parseUpdatePayload(
  payload: Record<string, unknown>,
): RealtimeViolationUpdateMessage | null {
  const lifecycleStatus = normalizeLifecycleStatus(payload.lifecycleStatus)
  const recordingStatus = normalizeRecordingStatus(payload.recordingStatus)

  if (
    !isNonEmptyString(payload.violationId) ||
    lifecycleStatus === null ||
    recordingStatus === null ||
    typeof payload.clipReady !== 'boolean' ||
    !isValidInstant(payload.updatedAt) ||
    !(
      payload.errorCode === undefined ||
      payload.errorCode === null ||
      typeof payload.errorCode === 'string'
    )
  ) {
    return null
  }

  return {
    violationId: payload.violationId,
    lifecycleStatus,
    recordingStatus,
    clipReady: payload.clipReady,
    updatedAt: payload.updatedAt,
    errorCode: payload.errorCode,
  }
}

export function parseRealtimePayload(body: string): ParsedRealtimePayload | null {
  let payload: unknown

  try {
    payload = JSON.parse(body)
  } catch {
    return null
  }

  if (!isRecord(payload)) {
    return null
  }

  if ('startedAt' in payload) {
    const alert = parseAlertPayload(payload)
    return alert ? { kind: 'ALERT', payload: alert } : null
  }

  if ('updatedAt' in payload) {
    const update = parseUpdatePayload(payload)
    return update ? { kind: 'VIOLATION_UPDATE', payload: update } : null
  }

  return null
}
