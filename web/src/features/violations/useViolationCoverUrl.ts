import { useCallback, useEffect, useState } from 'react'
import {
  getViolationCoverUrl,
  type ViolationCoverUrl,
} from '../../services/violationService'
import { getClipUrlRefreshDelay } from './clipUrlExpiry'

interface ViolationCoverUrlState {
  data: ViolationCoverUrl | null
  isLoading: boolean
  error: unknown
}

const emptyState: ViolationCoverUrlState = {
  data: null,
  isLoading: false,
  error: null,
}

export function useViolationCoverUrl(violationId: string, coverImageReady: boolean) {
  const [state, setState] = useState<ViolationCoverUrlState>(emptyState)
  const [retryVersion, setRetryVersion] = useState(0)

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    if (!coverImageReady) {
      return
    }

    let cancelled = false

    async function loadCoverUrl() {
      setState({
        data: null,
        isLoading: true,
        error: null,
      })

      try {
        const data = await getViolationCoverUrl(violationId)

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

    void loadCoverUrl()

    return () => {
      cancelled = true
    }
  }, [coverImageReady, retryVersion, violationId])

  useEffect(() => {
    if (!coverImageReady || !state.data) {
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
  }, [coverImageReady, retry, state.data])

  if (!coverImageReady) {
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