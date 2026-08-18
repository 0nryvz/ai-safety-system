import { useCallback, useEffect, useState } from 'react'
import {
  getViolationHistory,
  type PageResponse,
  type ViolationHistoryQuery,
  type ViolationListItem,
} from '../../services/violationService'

interface ViolationHistoryState {
  data: PageResponse<ViolationListItem> | null
  isLoading: boolean
  error: unknown
}

const initialState: ViolationHistoryState = {
  data: null,
  isLoading: true,
  error: null,
}

export function useViolationHistory(query: ViolationHistoryQuery) {
  const [state, setState] = useState<ViolationHistoryState>(initialState)

  const [retryVersion, setRetryVersion] = useState(0)

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadHistory() {
      setState((current) => ({
        ...current,
        isLoading: true,
        error: null,
      }))

      const currentQuery: ViolationHistoryQuery = {
        from: query.from,
        to: query.to,
        type: query.type,
        cameraId: query.cameraId,
        departmentId: query.departmentId,
        lifecycleStatus: query.lifecycleStatus,
        reviewStatus: query.reviewStatus,
        page: query.page,
        size: query.size,
        sort: query.sort,
      }

      try {
        const data = await getViolationHistory(currentQuery)

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

    void loadHistory()

    return () => {
      cancelled = true
    }
  }, [
    query.from,
    query.to,
    query.type,
    query.cameraId,
    query.departmentId,
    query.lifecycleStatus,
    query.reviewStatus,
    query.page,
    query.size,
    query.sort,
    retryVersion,
  ])

  return {
    ...state,
    retry,
  }
}
