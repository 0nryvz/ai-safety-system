import { RealtimeSeenEventCache } from './RealtimeSeenEventCache'
import { createRealtimeEventKey } from './realtimeEventKey'
import { parseRealtimePayload, type ParsedRealtimePayload } from './realtimePayloadParser'
import {
  initialRealtimeViolationState,
  realtimeViolationReducer,
  type RealtimeViolationState,
} from './realtimeViolationReducer'
import {
  reconcileRealtimeViolations,
  type RealtimeRecoverySnapshot,
} from './realtimeRecoveryReconciler'

export interface RealtimeEventStoreDiagnostic {
  code: 'INVALID_PAYLOAD' | 'UNKNOWN_ENUM_VALUE'
}

export interface RealtimeEventStoreOptions {
  cacheTtlMs?: number
  cacheMaxEntries?: number
  onDiagnostic?: (diagnostic: RealtimeEventStoreDiagnostic) => void
}

type RealtimeEventStoreListener = () => void

const DEFAULT_CACHE_TTL_MS = 5 * 60 * 1_000
const DEFAULT_CACHE_MAX_ENTRIES = 1_000

function containsUnknownEnum(event: ParsedRealtimePayload) {
  if (event.kind === 'ALERT') {
    return (
      event.payload.type === 'UNKNOWN' ||
      event.payload.lifecycleStatus === 'UNKNOWN' ||
      event.payload.recordingStatus === 'UNKNOWN'
    )
  }

  return event.payload.lifecycleStatus === 'UNKNOWN' || event.payload.recordingStatus === 'UNKNOWN'
}

export class RealtimeEventStore {
  private state: RealtimeViolationState = initialRealtimeViolationState
  private readonly listeners = new Set<RealtimeEventStoreListener>()
  private readonly seenEventCache: RealtimeSeenEventCache
  private readonly onDiagnostic?: (diagnostic: RealtimeEventStoreDiagnostic) => void

  constructor(options: RealtimeEventStoreOptions = {}) {
    this.onDiagnostic = options.onDiagnostic
    this.seenEventCache = new RealtimeSeenEventCache({
      ttlMs: options.cacheTtlMs ?? DEFAULT_CACHE_TTL_MS,
      maxEntries: options.cacheMaxEntries ?? DEFAULT_CACHE_MAX_ENTRIES,
    })
  }

  getSnapshot = (): RealtimeViolationState => {
    return this.state
  }

  subscribe = (listener: RealtimeEventStoreListener) => {
    this.listeners.add(listener)

    return () => {
      this.listeners.delete(listener)
    }
  }

  ingest(body: string): boolean {
    const event = parseRealtimePayload(body)

    if (!event) {
      this.onDiagnostic?.({
        code: 'INVALID_PAYLOAD',
      })

      return false
    }

    const eventKey = createRealtimeEventKey(event)

    if (this.seenEventCache.checkAndRemember(eventKey)) {
      return false
    }

    if (containsUnknownEnum(event)) {
      this.onDiagnostic?.({
        code: 'UNKNOWN_ENUM_VALUE',
      })
    }

    const nextState = realtimeViolationReducer(this.state, {
      type: 'EVENT_RECEIVED',
      event,
    })

    if (nextState === this.state) {
      return false
    }

    this.state = nextState
    this.publish()

    return true
  }

  dismiss(violationId: string): boolean {
    const nextState = realtimeViolationReducer(this.state, {
      type: 'DISMISS',
      violationId,
    })

    if (nextState === this.state) {
      return false
    }

    this.state = nextState
    this.publish()

    return true
  }

  reconcile(snapshots: RealtimeRecoverySnapshot[]): boolean {
    const nextState = reconcileRealtimeViolations(this.state, snapshots)

    if (nextState === this.state) {
      return false
    }

    this.state = nextState
    this.publish()

    return true
  }

  reset() {
    this.seenEventCache.clear()

    if (Object.keys(this.state.byId).length === 0) {
      return
    }

    this.state = initialRealtimeViolationState
    this.publish()
  }

  private publish() {
    this.listeners.forEach((listener) => {
      listener()
    })
  }
}
