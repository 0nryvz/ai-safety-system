import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../core/api/apiError'
import { getDashboardDistribution, getDashboardTrend } from '../../services/dashboardService'
import type {
  DashboardDistributionGroup,
  DashboardDistributionItem,
  DashboardTrendPoint,
} from './dashboardTypes'

interface DashboardAnalyticsState {
  trend: DashboardTrendPoint[]
  distribution: DashboardDistributionItem[]
  isTrendLoading: boolean
  isDistributionLoading: boolean
  error: ApiError | null
}

interface UseDashboardAnalyticsOptions {
  from: string
  to: string
  groupBy: DashboardDistributionGroup
}

const initialState: DashboardAnalyticsState = {
  trend: [],
  distribution: [],
  isTrendLoading: true,
  isDistributionLoading: true,
  error: null,
}

function normalizeAnalyticsError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  return new ApiError('Unexpected dashboard analytics loading error', -1)
}

export function useDashboardAnalytics({ from, to, groupBy }: UseDashboardAnalyticsOptions) {
  const [state, setState] = useState<DashboardAnalyticsState>(initialState)
  const [retryVersion, setRetryVersion] = useState(0)

  useEffect(() => {
    let isActive = true

    async function loadTrend() {
      setState((currentState) => ({
        ...currentState,
        isTrendLoading: true,
        error: null,
      }))

      try {
        const trend = await getDashboardTrend({
          from,
          to,
        })

        if (!isActive) {
          return
        }

        setState((currentState) => ({
          ...currentState,
          trend,
          isTrendLoading: false,
        }))
      } catch (error) {
        if (!isActive) {
          return
        }

        setState((currentState) => ({
          ...currentState,
          isTrendLoading: false,
          error: normalizeAnalyticsError(error),
        }))
      }
    }

    void loadTrend()

    return () => {
      isActive = false
    }
  }, [from, to, retryVersion])

  useEffect(() => {
    let isActive = true

    async function loadDistribution() {
      setState((currentState) => ({
        ...currentState,
        isDistributionLoading: true,
        error: null,
      }))

      try {
        const distribution = await getDashboardDistribution(groupBy)

        if (!isActive) {
          return
        }

        setState((currentState) => ({
          ...currentState,
          distribution,
          isDistributionLoading: false,
        }))
      } catch (error) {
        if (!isActive) {
          return
        }

        setState((currentState) => ({
          ...currentState,
          isDistributionLoading: false,
          error: normalizeAnalyticsError(error),
        }))
      }
    }

    void loadDistribution()

    return () => {
      isActive = false
    }
  }, [groupBy, retryVersion])

  const retry = useCallback(() => {
    setState((currentState) => ({
      ...currentState,
      isTrendLoading: true,
      isDistributionLoading: true,
      error: null,
    }))

    setRetryVersion((currentVersion) => currentVersion + 1)
  }, [])

  return {
    trend: state.trend,
    distribution: state.distribution,
    isLoading: state.isTrendLoading || state.isDistributionLoading,
    error: state.error,
    retry,
  }
}
