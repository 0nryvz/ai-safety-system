import { useCallback, useEffect, useState } from 'react'
import type { RealtimeRecordingStatus } from '../../core/realtime/realtimeTypes'
import { getViolationClipUrl, type ViolationClipUrl } from '../../services/violationService'
import { getClipUrlRefreshDelay } from './clipUrlExpiry'

interface ViolationClipUrlState {
  data: ViolationClipUrl | null
  isLoading: boolean
  error: unknown
}

const emptyState: ViolationClipUrlState = {
  data: null,
  isLoading: false,
  error: null,
}

export function useViolationClipUrl(violationId: string, recordingStatus: RealtimeRecordingStatus) {
  const [state, setState] = useState<ViolationClipUrlState>(emptyState)
  const [retryVersion, setRetryVersion] = useState(0)

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    if (recordingStatus !== 'READY') {
      return
    }

    let cancelled = false

    async function loadClipUrl() {
      setState({
        data: null,
        isLoading: true,
        error: null,
      })

      try {
        const data = await getViolationClipUrl(violationId)

        if (cancelled) {
          return
        }

        setState({
          data,
          isLoading: false,
          error: null,
        })
      } catch (error) {
        if (cancelled) {
          return
        }

        setState({
          data: null,
          isLoading: false,
          error,
        })
      }
    }

    void loadClipUrl()

    return () => {
      cancelled = true
    }
  }, [recordingStatus, retryVersion, violationId])

  useEffect(() => {
    if (recordingStatus !== 'READY' || !state.data) {
      return
    }

    const refreshDelay = getClipUrlRefreshDelay(state.data.expiresAt)

    if (refreshDelay === null) {
      return
    }

    const timerId = window.setTimeout(retry, refreshDelay)

    return () => {
      window.clearTimeout(timerId)
    }
  }, [recordingStatus, retry, state.data])

  if (recordingStatus !== 'READY') {
    return {
      ...emptyState,
      retry,
    }
  }

  return {
    ...state,
    retry,
  }
}
