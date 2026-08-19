import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../core/api/apiError'
import {
  getCameras,
  getDashboardSummary,
  getRecentViolations,
} from '../../services/dashboardService'
import type { Camera, DashboardSummary, RecentViolation } from './dashboardTypes'

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
