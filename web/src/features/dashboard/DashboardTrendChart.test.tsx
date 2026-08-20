import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import DashboardTrendChart from './DashboardTrendChart'

afterEach(() => {
  cleanup()
})

describe('DashboardTrendChart', () => {
  it('renders the daily trend values', () => {
    render(
      <DashboardTrendChart
        data={[
          {
            date: '2026-08-18',
            count: 4,
          },
          {
            date: '2026-08-19',
            count: 7,
          },
        ]}
      />,
    )

    expect(
      screen.getByRole('img', {
        name: 'Günlük ihlal sayısı trend grafiği',
      }),
    ).toBeInTheDocument()

    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(screen.getByText('18.08')).toBeInTheDocument()
    expect(screen.getByText('19.08')).toBeInTheDocument()
  })

  it('renders an empty state when trend data is empty', () => {
    render(<DashboardTrendChart data={[]} />)

    expect(
      screen.getByText('Seçilen tarih aralığında trend verisi bulunmuyor.'),
    ).toBeInTheDocument()

    expect(
      screen.queryByRole('img', {
        name: 'Günlük ihlal sayısı trend grafiği',
      }),
    ).not.toBeInTheDocument()
  })

  it('supports a single trend point', () => {
    render(
      <DashboardTrendChart
        data={[
          {
            date: '2026-08-20',
            count: 5,
          },
        ]}
      />,
    )

    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('20.08')).toBeInTheDocument()
  })
})
