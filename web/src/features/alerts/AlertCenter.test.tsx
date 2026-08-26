import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { useRealtimeViolations } from '../../core/realtime/useRealtimeViolations'
import AlertCenter from './AlertCenter'

function LocationProbe() {
  const location = useLocation()

  return <div data-testid="location-pathname">{location.pathname}</div>
}

function renderAlertCenter() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <AlertCenter />
      <LocationProbe />
    </MemoryRouter>,
  )
}

vi.mock('../../core/realtime/useRealtimeViolations', () => ({
  useRealtimeViolations: vi.fn(),
}))

const mockedUseRealtimeViolations = vi.mocked(useRealtimeViolations)

const dismissViolation = vi.fn()

afterEach(() => {
  cleanup()
})

const violation = {
  eventId: 'violation-1',
  version: 1,
  violationId: 'violation-1',
  type: 'MISSING_GLOVES' as const,
  cameraName: 'Üretim Kamerası',
  departmentName: 'Üretim',
  startedAt: '2026-08-18T18:00:00Z',
  confidence: 0.94,
  lifecycleStatus: 'ACTIVE' as const,
  recordingStatus: 'REQUESTED' as const,
  clipReady: false,
  coverImageReady: false,
  lastEventAt: '2026-08-18T18:00:00Z',
  dismissed: false,
  errorCode: null,
}

describe('AlertCenter', () => {
  beforeEach(() => {
    dismissViolation.mockReset()

    mockedUseRealtimeViolations.mockReturnValue({
      violations: [],
      dismissViolation,
    })
    window.localStorage.clear()
  })

  it('shows an empty state when there are no active alerts', () => {
    renderAlertCenter()

    fireEvent.click(screen.getByRole('button', { name: /bildirimleri aç/i }))

    expect(screen.getByText('Aktif güvenlik bildirimi bulunmuyor.')).toBeInTheDocument()
  })

  it('shows active alerts and dismisses the selected alert', () => {
    mockedUseRealtimeViolations.mockReturnValue({
      violations: [violation],
      dismissViolation,
    })

    renderAlertCenter()

    fireEvent.click(screen.getByRole('button', { name: /bildirimleri aç/i }))

    expect(screen.getByText('Koruyucu eldiven eksik')).toBeInTheDocument()
    expect(screen.getByText('Üretim Kamerası · Üretim')).toBeInTheDocument()
    expect(screen.getByText('Güven: %94')).toBeInTheDocument()
    expect(screen.getByText('Kayıt hazırlanıyor')).toBeInTheDocument()
    expect(screen.getByText('Kapak hazırlanıyor')).toBeInTheDocument()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Üretim Kamerası bildirimini kapat',
      }),
    )

    expect(dismissViolation).toHaveBeenCalledWith('violation-1')
    expect(screen.getByTestId('location-pathname')).toHaveTextContent('/dashboard')
  })

  it('navigates to the existing violation detail route from the card body', async () => {
    const user = userEvent.setup()

    mockedUseRealtimeViolations.mockReturnValue({
      violations: [violation],
      dismissViolation,
    })

    renderAlertCenter()

    fireEvent.click(screen.getByRole('button', { name: /bildirimleri aç/i }))

    await user.click(screen.getByRole('button', { name: /koruyucu eldiven eksik/i }))

    expect(screen.getByTestId('location-pathname')).toHaveTextContent('/violations/violation-1')
    expect(dismissViolation).not.toHaveBeenCalled()
  })

  it('opens the same detail route with Enter and Space on the card body', async () => {
    const user = userEvent.setup()

    mockedUseRealtimeViolations.mockReturnValue({
      violations: [violation],
      dismissViolation,
    })

    renderAlertCenter()

    fireEvent.click(screen.getByRole('button', { name: /bildirimleri aç/i }))

    const card = screen.getByRole('button', { name: /koruyucu eldiven eksik/i })
    card.focus()
    await user.keyboard('{Enter}')

    expect(screen.getByTestId('location-pathname')).toHaveTextContent('/violations/violation-1')

    fireEvent.click(screen.getByRole('button', { name: /bildirimleri aç/i }))

    const openedCard = screen.getByRole('button', { name: /koruyucu eldiven eksik/i })
    openedCard.focus()
    await user.keyboard(' ')

    expect(screen.getByTestId('location-pathname')).toHaveTextContent('/violations/violation-1')
    expect(dismissViolation).not.toHaveBeenCalled()
  })

  it('does not navigate when the notification has no violationId', async () => {
    const user = userEvent.setup()

    mockedUseRealtimeViolations.mockReturnValue({
      violations: [
        {
          ...violation,
          violationId: '',
        },
      ],
      dismissViolation,
    })

    renderAlertCenter()

    fireEvent.click(screen.getByRole('button', { name: /bildirimleri aç/i }))

    await user.click(screen.getByRole('button', { name: /koruyucu eldiven eksik/i }))

    expect(screen.getByTestId('location-pathname')).toHaveTextContent('/dashboard')
    expect(dismissViolation).not.toHaveBeenCalled()
  })

  it('persists the alert sound preference', () => {
    renderAlertCenter()

    fireEvent.click(screen.getByRole('button', { name: /bildirimleri aç/i }))

    const muteButton = screen.getByRole('button', {
      name: 'Bildirim sesini kapat',
    })

    fireEvent.click(muteButton)

    expect(
      screen.getByRole('button', {
        name: 'Bildirim sesini aç',
      }),
    ).toHaveAttribute('aria-pressed', 'true')

    expect(window.localStorage.getItem('ai-safety.alert-sound-muted')).toBe('true')
  })

  it('does not include dismissed alerts in the active count', () => {
    mockedUseRealtimeViolations.mockReturnValue({
      violations: [
        {
          ...violation,
          dismissed: true,
        },
      ],
      dismissViolation,
    })

    renderAlertCenter()

    expect(
      screen.getByRole('button', {
        name: 'Bildirimleri aç. 0 aktif bildirim var.',
      }),
    ).toBeInTheDocument()
  })

  it('closes the panel with Escape or an outside pointer action', () => {
    renderAlertCenter()

    const trigger = screen.getByRole('button', {
      name: /bildirimleri aç/i,
    })

    fireEvent.click(trigger)

    expect(
      screen.getByRole('region', {
        name: 'Aktif güvenlik bildirimleri',
      }),
    ).toBeInTheDocument()

    fireEvent.keyDown(document, {
      key: 'Escape',
    })

    expect(
      screen.queryByRole('region', {
        name: 'Aktif güvenlik bildirimleri',
      }),
    ).not.toBeInTheDocument()

    fireEvent.click(trigger)

    fireEvent.pointerDown(document.body)

    expect(
      screen.queryByRole('region', {
        name: 'Aktif güvenlik bildirimleri',
      }),
    ).not.toBeInTheDocument()
  })
})
