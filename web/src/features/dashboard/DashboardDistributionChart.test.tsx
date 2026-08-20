import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import DashboardDistributionChart from './DashboardDistributionChart'

afterEach(() => {
  cleanup()
})

describe('DashboardDistributionChart', () => {
  it('renders distribution groups and counts', () => {
    render(
      <DashboardDistributionChart
        data={[
          {
            group: 'NO_HELMET',
            count: 8,
          },
          {
            group: 'RESTRICTED_ZONE',
            count: 3,
          },
        ]}
      />,
    )

    expect(
      screen.getByRole('img', {
        name: 'İhlal dağılım grafiği',
      }),
    ).toBeInTheDocument()

    expect(screen.getByText('NO_HELMET')).toBeInTheDocument()
    expect(screen.getByText('RESTRICTED_ZONE')).toBeInTheDocument()
    expect(screen.getByText('8')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('renders an empty state when distribution data is empty', () => {
    render(<DashboardDistributionChart data={[]} />)

    expect(screen.getByText('Dağılım verisi bulunmuyor.')).toBeInTheDocument()

    expect(
      screen.queryByRole('img', {
        name: 'İhlal dağılım grafiği',
      }),
    ).not.toBeInTheDocument()
  })
})
