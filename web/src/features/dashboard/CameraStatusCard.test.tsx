import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Camera } from './dashboardTypes'
import CameraStatusCard from './CameraStatusCard'

vi.mock('../../core/date/dateTime', () => ({
  formatUtcToLocal: vi.fn(() => '18.08.2026 17:00'),
}))

const camera: Camera = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Montaj Kamera 1',
  code: 'CAM-001',
  departmentId: '22222222-2222-2222-2222-222222222222',
  departmentName: 'Montaj',
  active: true,
  connectionStatus: 'ONLINE',
  lastSeenAt: '2026-08-18T14:00:00Z',
  activeSessionId: null,
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('CameraStatusCard', () => {
  it('renders the backend camera status and last seen time', () => {
    render(<CameraStatusCard camera={camera} />)

    expect(screen.getByRole('heading', { name: 'Montaj Kamera 1' })).toBeInTheDocument()
    expect(screen.getByText('CAM-001')).toBeInTheDocument()
    expect(screen.getByText('Çevrimiçi')).toHaveClass('ui-status-badge--success')
    expect(screen.getByText('18.08.2026 17:00')).toBeInTheDocument()
    expect(screen.getByText('Aktif')).toBeInTheDocument()
    expect(screen.getByText('Montaj')).toBeInTheDocument()
  })

  it('renders safe fallbacks for an inactive camera without last seen data', () => {
    render(
      <CameraStatusCard
        camera={{
          ...camera,
          active: false,
          connectionStatus: 'FUTURE_STATUS',
          lastSeenAt: null,
          departmentName: null,
        }}
      />,
    )

    expect(screen.getByText('Bilinmiyor')).toHaveClass('ui-status-badge--neutral')
    expect(screen.getByText('Pasif')).toBeInTheDocument()
    expect(screen.getByText('Henüz görülmedi')).toBeInTheDocument()
    expect(screen.getByText('Departman bilgisi bekleniyor')).toBeInTheDocument()
  })
})
