import { useCallback, useMemo, useSyncExternalStore } from 'react'
import { realtimeEventStore } from './realtimeRuntime'

export function useRealtimeViolations() {
  const state = useSyncExternalStore(
    (listener) => realtimeEventStore.subscribe(listener),
    () => realtimeEventStore.getSnapshot(),
    () => realtimeEventStore.getSnapshot(),
  )

  const violations = useMemo(() => Object.values(state.byId), [state])

  const dismissViolation = useCallback((violationId: string) => {
    realtimeEventStore.dismiss(violationId)
  }, [])

  return {
    violations,
    dismissViolation,
  }
}
