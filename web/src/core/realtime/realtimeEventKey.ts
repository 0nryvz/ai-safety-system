import type { ParsedRealtimePayload } from './realtimePayloadParser'

export function createRealtimeEventKey(event: ParsedRealtimePayload): string {
  if (event.kind === 'ALERT') {
    const payload = event.payload

    return JSON.stringify([
      event.kind,
      payload.violationId,
      payload.startedAt,
      payload.type,
      payload.cameraName,
      payload.departmentName,
      payload.confidence,
      payload.lifecycleStatus,
      payload.recordingStatus,
      payload.clipReady,
      payload.coverImageReady,
    ])
  }

  const payload = event.payload

  return JSON.stringify([
    event.kind,
    payload.violationId,
    payload.updatedAt,
    payload.lifecycleStatus,
    payload.recordingStatus,
    payload.clipReady,
    payload.errorCode ?? null,
  ])
}
