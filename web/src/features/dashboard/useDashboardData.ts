import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../core/api/apiError'
import {
  getCameras,
  getDashboardSummary,
  getRecentViolations,
} from '../../services/dashboardService'
import type { Camera, DashboardSummary, RecentViolation } from './dashboardTypes'
import { subscribeToRealtimeRecovery } from '../../core/realtime/realtimeRuntime'
import { useRealtimeRestRefresh } from '../../core/realtime/useRealtimeRestRefresh'

interface UseDashboardDataOptions {
  includeSummary: boolean
}

interface DashboardDataState {
  summary: DashboardSummary | null
  recentViolations: RecentViolation[]
  cameras: Camera[]
  isLoading: boolean
  error: ApiError | null
}

const initialState: DashboardDataState = {
  summary: null,
  recentViolations: [],
  cameras: [],
  isLoading: true,
  error: null,
}

function normalizeDashboardError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  return new ApiError('Unexpected dashboard loading error', -1)
}

export function useDashboardData({ includeSummary }: UseDashboardDataOptions) {
  const [state, setState] = useState<DashboardDataState>(initialState)
  const [requestVersion, setRequestVersion] = useState(0)
  const backgroundRefreshInFlightRef = useRef(false)
  const isMountedRef = useRef(true)

  useEffect(() => {
    isMountedRef.current = true

    return () => {
      isMountedRef.current = false
    }
  }, [])

  useRealtimeRestRefresh(
    () => {
      void getRecentViolations()
        .then((recentViolations) => {
          if (!isMountedRef.current) {
            return
          }

          setState((currentState) => ({
            ...currentState,
            recentViolations,
          }))
        })
        .catch(() => {
          // Background refresh failure keeps the last known recent violations visible.
        })
    },
    {
      onRecovery: false,
    },
  )

  useEffect(() => {
    let isActive = true

    async function loadDashboardData() {
      try {
        const summaryRequest = includeSummary ? getDashboardSummary() : Promise.resolve(null)

        const [summary, recentViolations, cameras] = await Promise.all([
          summaryRequest,
          getRecentViolations(),
          getCameras(),
        ])

        if (!isActive) {
          return
        }

        setState({
          summary,
          recentViolations,
          cameras,
          isLoading: false,
          error: null,
        })
      } catch (error) {
        if (!isActive) {
          return
        }

        setState((currentState) => ({
          ...currentState,
          isLoading: false,
          error: normalizeDashboardError(error),
        }))
      }
    }

    void loadDashboardData()

    return () => {
      isActive = false
    }
  }, [includeSummary, requestVersion])

  useEffect(() => {
    let isActive = true

    const intervalId = window.setInterval(() => {
      if (backgroundRefreshInFlightRef.current) {
        return
      }

      backgroundRefreshInFlightRef.current = true

      const summaryRefresh = includeSummary
        ? getDashboardSummary()
            .then((summary) => {
              if (!isActive) {
                return
              }

              setState((currentState) => ({
                ...currentState,
                summary,
              }))
            })
            .catch(() => {
              // Background refresh failure keeps the last known dashboard state visible.
            })
        : Promise.resolve()

      const camerasRefresh = getCameras()
        .then((cameras) => {
          if (!isActive) {
            return
          }

          setState((currentState) => ({
            ...currentState,
            cameras,
          }))
        })
        .catch(() => {
          // Background camera refresh failure keeps the last known camera list visible.
        })

      void Promise.allSettled([summaryRefresh, camerasRefresh]).finally(() => {
        backgroundRefreshInFlightRef.current = false
      })
    }, 10_000)

    return () => {
      isActive = false
      window.clearInterval(intervalId)
    }
  }, [includeSummary])

  useEffect(() => {
    return subscribeToRealtimeRecovery(() => {
      setRequestVersion((currentVersion) => currentVersion + 1)
    })
  }, [])

  const retry = useCallback(() => {
    setState((currentState) => ({
      ...currentState,
      isLoading: true,
      error: null,
    }))
    setRequestVersion((currentVersion) => currentVersion + 1)
  }, [])

  return {
    ...state,
    retry,
  }
}
