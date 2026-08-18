import { useEffect, useRef } from 'react'
import type { RealtimeViolationRecord } from '../../core/realtime/realtimeViolationReducer'
import { playAlertSound } from './alertSound'

export function useAlertSound(violations: RealtimeViolationRecord[], isSoundMuted: boolean): void {
  const hasUserInteracted = useRef(false)
  const hasInitialized = useRef(false)
  const seenViolationIds = useRef(new Set<string>())

  useEffect(() => {
    function markUserInteraction() {
      hasUserInteracted.current = true
      window.removeEventListener('pointerdown', markUserInteraction)
      window.removeEventListener('keydown', markUserInteraction)
    }

    window.addEventListener('pointerdown', markUserInteraction)
    window.addEventListener('keydown', markUserInteraction)

    return () => {
      window.removeEventListener('pointerdown', markUserInteraction)
      window.removeEventListener('keydown', markUserInteraction)
    }
  }, [])

  useEffect(() => {
    const activeViolationIds = violations
      .filter((violation) => !violation.dismissed)
      .map((violation) => violation.violationId)

    const hasNewViolation = activeViolationIds.some(
      (violationId) => !seenViolationIds.current.has(violationId),
    )

    activeViolationIds.forEach((violationId) => {
      seenViolationIds.current.add(violationId)
    })

    if (hasInitialized.current && hasNewViolation && hasUserInteracted.current && !isSoundMuted) {
      playAlertSound()
    }

    hasInitialized.current = true
  }, [isSoundMuted, violations])
}
