import { useCallback, useEffect, useRef, useState } from 'react'
import { useRealtimeRestRefresh } from '../../core/realtime/useRealtimeRestRefresh'
import {
  getViolationHistory,
  type PageResponse,
  type ViolationHistoryQuery,
  type ViolationListItem,
} from '../../services/violationService'

function toHistoryQuery(query: ViolationHistoryQuery): ViolationHistoryQuery {
  return {
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
}

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
  const [backgroundVersion, setBackgroundVersion] = useState(0)
  const queryRef = useRef(query)
  const requestGenerationRef = useRef(0)

  useEffect(() => {
    queryRef.current = query
  }, [query])

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useRealtimeRestRefresh(() => {
    setBackgroundVersion((current) => current + 1)
  })

  useEffect(() => {
    let cancelled = false
    const requestGeneration = ++requestGenerationRef.current

    async function loadHistory() {
      setState((current) => ({
        ...current,
        isLoading: true,
        error: null,
      }))

      const currentQuery = toHistoryQuery({
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
      })

      try {
        const data = await getViolationHistory(currentQuery)

        if (cancelled || requestGeneration !== requestGenerationRef.current) {
          return
        }

        setState({
          data,
          isLoading: false,
          error: null,
        })
      } catch (error) {
        if (cancelled || requestGeneration !== requestGenerationRef.current) {
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

  useEffect(() => {
    if (backgroundVersion === 0) {
      return
    }

    let cancelled = false
    const requestGeneration = ++requestGenerationRef.current
    const currentQuery = toHistoryQuery(queryRef.current)

    async function refreshHistory() {
      try {
        const data = await getViolationHistory(currentQuery)

        if (cancelled || requestGeneration !== requestGenerationRef.current) {
          return
        }

        setState({
          data,
          isLoading: false,
          error: null,
        })
      } catch {
        // Background refresh failure keeps the last known history visible.
      }
    }

    void refreshHistory()

    return () => {
      cancelled = true
    }
  }, [backgroundVersion])

  return {
    ...state,
    retry,
  }
}
