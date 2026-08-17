import type { ParsedRealtimePayload } from './realtimePayloadParser'
import type {
  RealtimeAlertMessage,
  RealtimeLifecycleStatus,
  RealtimeRecordingStatus,
} from './realtimeTypes'

export interface RealtimeViolationRecord extends RealtimeAlertMessage {
  lifecycleStatus: RealtimeLifecycleStatus
  recordingStatus: RealtimeRecordingStatus
  lastEventAt: string
  dismissed: boolean
  errorCode: string | null
}

export interface RealtimeViolationState {
  byId: Record<string, RealtimeViolationRecord>
}

export type RealtimeViolationAction =
  | {
      type: 'EVENT_RECEIVED'
      event: ParsedRealtimePayload
    }
  | {
      type: 'DISMISS'
      violationId: string
    }
  | {
      type: 'RESET'
    }

export const initialRealtimeViolationState: RealtimeViolationState = {
  byId: {},
}

export function realtimeViolationReducer(
  state: RealtimeViolationState,
  action: RealtimeViolationAction,
): RealtimeViolationState {
  if (action.type === 'RESET') {
    return initialRealtimeViolationState
  }

  if (action.type === 'DISMISS') {
    const currentViolation = state.byId[action.violationId]

    if (!currentViolation || currentViolation.dismissed) {
      return state
    }

    return {
      ...state,
      byId: {
        ...state.byId,
        [action.violationId]: {
          ...currentViolation,
          dismissed: true,
        },
      },
    }
  }

  const event = action.event

  if (event.kind === 'ALERT') {
    const currentViolation = state.byId[event.payload.violationId]

    if (currentViolation) {
      return state
    }

    return {
      ...state,
      byId: {
        ...state.byId,
        [event.payload.violationId]: {
          ...event.payload,
          lastEventAt: event.payload.startedAt,
          dismissed: false,
          errorCode: null,
        },
      },
    }
  }

  const currentViolation = state.byId[event.payload.violationId]

  if (!currentViolation) {
    return state
  }

  const currentEventTime = Date.parse(currentViolation.lastEventAt)
  const incomingEventTime = Date.parse(event.payload.updatedAt)

  if (incomingEventTime <= currentEventTime) {
    return state
  }

  return {
    ...state,
    byId: {
      ...state.byId,
      [event.payload.violationId]: {
        ...currentViolation,
        lifecycleStatus: event.payload.lifecycleStatus,
        recordingStatus: event.payload.recordingStatus,
        clipReady: event.payload.clipReady,
        lastEventAt: event.payload.updatedAt,
        errorCode: event.payload.errorCode ?? null,
      },
    },
  }
}
