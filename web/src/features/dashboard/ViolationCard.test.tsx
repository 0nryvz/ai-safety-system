import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DashboardViolation } from './dashboardViolationModel'
import ViolationCard from './ViolationCard'

vi.mock('../../core/date/dateTime', () => ({
  formatUtcToLocal: vi.fn(() => '18.08.2026 17:30'),
}))

const restViolation: DashboardViolation = {
  violationId: 'violation-1',
  violationType: 'MISSING_GLOVES',
  cameraName: 'Kamera 1',
  departmentName: null,
  occurredAt: '2026-08-18T14:30:00Z',
  lifecycleStatus: 'ACTIVE',
  recordingStatus: 'REQUESTED',
  confidence: 0.94,
  source: 'REST',
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ViolationCard', () => {
  it('renders a REST violation without guessing the department name', () => {
    render(<ViolationCard violation={restViolation} />)

    expect(screen.getByRole('heading', { name: 'Eldiven eksik' })).toBeInTheDocument()
    expect(screen.getByText('Kamera 1')).toBeInTheDocument()
    expect(screen.getByText('Bölüm bilgisi bekleniyor')).toBeInTheDocument()
    expect(screen.getByText('18.08.2026 17:30')).toBeInTheDocument()
    expect(screen.getByText('%94')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Uyarıyı kapat' })).not.toBeInTheDocument()
  })

  it('dismisses only a realtime violation through the supplied callback', () => {
    const onDismiss = vi.fn()

    render(
      <ViolationCard
        violation={{
          ...restViolation,
          departmentName: 'Montaj',
          recordingStatus: 'READY',
          source: 'REALTIME',
        }}
        onDismiss={onDismiss}
      />,
    )

    expect(screen.getByText('Montaj')).toBeInTheDocument()
    expect(screen.getByText('Hazır')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Uyarıyı kapat' }))

    expect(onDismiss).toHaveBeenCalledWith('violation-1')
  })
})
