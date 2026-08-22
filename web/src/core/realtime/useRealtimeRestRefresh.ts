import { useEffect, useRef } from 'react'
import { subscribeToRealtimeMessages, subscribeToRealtimeRecovery } from './realtimeRuntime'

export const REALTIME_REST_REFRESH_DEBOUNCE_MS = 300

interface UseRealtimeRestRefreshOptions {
  debounceMs?: number
  onMessages?: boolean
  onRecovery?: boolean
}

export function useRealtimeRestRefresh(
  refresh: () => void,
  options: UseRealtimeRestRefreshOptions = {},
) {
  const {
    debounceMs = REALTIME_REST_REFRESH_DEBOUNCE_MS,
    onMessages = true,
    onRecovery = true,
  } = options

  const refreshRef = useRef(refresh)
  refreshRef.current = refresh

  useEffect(() => {
    let timeoutId: number | null = null

    function scheduleRefresh() {
      if (timeoutId !== null) {
        window.clearTimeout(timeoutId)
      }

      timeoutId = window.setTimeout(() => {
        timeoutId = null
        refreshRef.current()
      }, debounceMs)
    }

    const unsubscribers: Array<() => void> = []

    if (onMessages) {
      unsubscribers.push(subscribeToRealtimeMessages(scheduleRefresh))
    }

    if (onRecovery) {
      unsubscribers.push(subscribeToRealtimeRecovery(scheduleRefresh))
    }

    return () => {
      if (timeoutId !== null) {
        window.clearTimeout(timeoutId)
      }

      unsubscribers.forEach((unsubscribe) => {
        unsubscribe()
      })
    }
  }, [debounceMs, onMessages, onRecovery])
}
