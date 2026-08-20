import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../core/api/apiError'
import { useDashboardAnalytics } from './useDashboardAnalytics'
import DashboardAnalyticsPanel from './DashboardAnalyticsPanel'

vi.mock('./useDashboardAnalytics', () => ({
  useDashboardAnalytics: vi.fn(),
}))

const mockedUseDashboardAnalytics = vi.mocked(useDashboardAnalytics)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('DashboardAnalyticsPanel', () => {
  it('renders trend and distribution analytics', () => {
    mockedUseDashboardAnalytics.mockReturnValue({
      trend: [
        {
          date: '2026-08-18',
          count: 4,
        },
      ],
      distribution: [
        {
          group: 'NO_HELMET',
          count: 3,
        },
      ],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    render(<DashboardAnalyticsPanel />)

    expect(
      screen.getByRole('heading', {
        name: 'Analitik görünüm',
      }),
    ).toBeInTheDocument()

    expect(
      screen.getByRole('heading', {
        name: 'Günlük ihlal trendi',
      }),
    ).toBeInTheDocument()

    expect(screen.getByText('NO_HELMET')).toBeInTheDocument()
  })

  it('changes the distribution grouping', () => {
    mockedUseDashboardAnalytics.mockReturnValue({
      trend: [],
      distribution: [],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    render(<DashboardAnalyticsPanel />)

    fireEvent.change(
      screen.getByRole('combobox', {
        name: 'Dağılım türü',
      }),
      {
        target: {
          value: 'CAMERA',
        },
      },
    )

    expect(mockedUseDashboardAnalytics).toHaveBeenLastCalledWith(
      expect.objectContaining({
        groupBy: 'CAMERA',
      }),
    )
  })

  it('renders analytics loading state', () => {
    mockedUseDashboardAnalytics.mockReturnValue({
      trend: [],
      distribution: [],
      isLoading: true,
      error: null,
      retry: vi.fn(),
    })

    render(<DashboardAnalyticsPanel />)

    expect(screen.getByRole('status')).toHaveTextContent('Analitik veriler yükleniyor...')
  })

  it('retries analytics after an error', () => {
    const retry = vi.fn()

    mockedUseDashboardAnalytics.mockReturnValue({
      trend: [],
      distribution: [],
      isLoading: false,
      error: new ApiError('Analytics error', 500),
      retry,
    })

    render(<DashboardAnalyticsPanel />)

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Yeniden dene',
      }),
    )

    expect(retry).toHaveBeenCalledTimes(1)
  })
})
