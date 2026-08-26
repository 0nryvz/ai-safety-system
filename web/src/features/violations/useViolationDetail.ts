import { useCallback, useEffect, useState } from 'react'
import { getViolationDetail, type ViolationDetailResponse } from '../../services/violationService'

interface ViolationDetailState {
  data: ViolationDetailResponse | null
  isLoading: boolean
  error: unknown
}

const initialState: ViolationDetailState = {
  data: null,
  isLoading: true,
  error: null,
}

export function useViolationDetail(violationId: string) {
  const [state, setState] = useState<ViolationDetailState>(initialState)
  const [retryVersion, setRetryVersion] = useState(0)

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadDetail() {
      setState((current) => ({
        ...current,
        isLoading: true,
        error: null,
      }))

      try {
        const data = await getViolationDetail(violationId)

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

    void loadDetail()

    return () => {
      cancelled = true
    }
  }, [violationId, retryVersion])

  return {
    ...state,
    retry,
  }
}
