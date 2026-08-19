import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../core/api/apiError'
import type {
  Camera,
  DashboardSummary,
  RecentViolation,
} from '../features/dashboard/dashboardTypes'
import { useDashboardData } from '../features/dashboard/useDashboardData'
import type { UserRole } from '../features/auth/sessionTypes'
import { useAuthSession } from '../features/auth/useAuthSession'
import DashboardPage from './DashboardPage'
import type { RealtimeViolationRecord } from '../core/realtime/realtimeViolationReducer'
import { useRealtimeViolations } from '../core/realtime/useRealtimeViolations'

vi.mock('../features/dashboard/useDashboardData', () => ({
  useDashboardData: vi.fn(),
}))

vi.mock('../features/auth/useAuthSession', () => ({
  useAuthSession: vi.fn(),
}))

vi.mock('../core/realtime/useRealtimeViolations', () => ({
  useRealtimeViolations: vi.fn(),
}))

const mockedUseDashboardData = vi.mocked(useDashboardData)
const mockedUseAuthSession = vi.mocked(useAuthSession)
const mockedUseRealtimeViolations = vi.mocked(useRealtimeViolations)

const summary: DashboardSummary = {
  todayViolationCount: 4,
  last7DaysViolationCount: 18,
  mostFrequentViolationType: 'NO_HELMET',
  activeCameraCount: 6,
  offlineCameraCount: 2,
  activeViolationCount: 3,
}

const camera: Camera = {
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Montaj Kamera 1',
  code: 'CAM-001',
  departmentId: '33333333-3333-3333-3333-333333333333',
  departmentName: 'Montaj',
  active: true,
  connectionStatus: 'ONLINE',
  lastSeenAt: null,
  activeSessionId: null,
}

const recentViolation: RecentViolation = {
  violationId: 'violation-1',
  detectedAt: '2026-08-18T12:00:00Z',
  startedAt: '2026-08-18T12:01:00Z',
  violationType: 'MISSING_GLOVES',
  cameraId: 'camera-1',
  departmentId: 'department-1',
  departmentName: 'Montaj',
  cameraName: 'Kamera 1',
  cameraCode: 'CAM-001',
  lifecycleStatus: 'ACTIVE',
  reviewStatus: null,
  recordingStatus: 'REQUESTED',
  recordingReadyAt: null,
  confidence: 0.88,
  modelVersion: null,
}

const realtimeViolation: RealtimeViolationRecord = {
  violationId: 'violation-1',
  type: 'MISSING_GLOVES',
  cameraName: 'Kamera 1',
  departmentName: 'Montaj',
  startedAt: '2026-08-18T12:01:00Z',
  confidence: 0.94,
  lifecycleStatus: 'COMPLETED',
  recordingStatus: 'READY',
  clipReady: true,
  coverImageReady: true,
  lastEventAt: '2026-08-18T12:05:00Z',
  dismissed: false,
  errorCode: null,
}

function setAuthenticatedRole(role: UserRole) {
  mockedUseAuthSession.mockReturnValue({
    status: 'authenticated',
    session: {
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      tokenType: 'Bearer',
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        email: 'user@example.com',
        fullName: 'Test User',
        active: true,
        roles: [role],
        departmentIds: [],
      },
    },
  })
}

function createDashboardState(
  overrides: Partial<ReturnType<typeof useDashboardData>> = {},
): ReturnType<typeof useDashboardData> {
  return {
    summary: null,
    recentViolations: [],
    cameras: [],
    isLoading: false,
    error: null,
    retry: vi.fn(),
    ...overrides,
  }
}

beforeEach(() => {
  mockedUseRealtimeViolations.mockReturnValue({
    violations: [],
    dismissViolation: vi.fn(),
  })
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderDashboard() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  it('loads and displays summary metrics for an admin', () => {
    setAuthenticatedRole('ADMIN')
    mockedUseDashboardData.mockReturnValue(createDashboardState({ summary }))

    renderDashboard()

    expect(mockedUseDashboardData).toHaveBeenCalledWith({
      includeSummary: true,
    })
    expect(screen.getByRole('heading', { name: 'Genel durum' })).toBeInTheDocument()
    expect(screen.getByText('Bugünkü ihlaller')).toBeInTheDocument()
    expect(screen.getByText('NO_HELMET')).toBeInTheDocument()
  })

  it('does not request global summary metrics for a restricted role', () => {
    setAuthenticatedRole('OHS_SPECIALIST')
    mockedUseDashboardData.mockReturnValue(createDashboardState())

    renderDashboard()

    expect(mockedUseDashboardData).toHaveBeenCalledWith({
      includeSummary: false,
    })
    expect(
      screen.getByRole('heading', {
        name: 'Özet metrikler kullanıma hazır değil',
      }),
    ).toBeInTheDocument()
  })

  it('renders the loading state', () => {
    setAuthenticatedRole('ADMIN')
    mockedUseDashboardData.mockReturnValue(
      createDashboardState({
        isLoading: true,
      }),
    )

    renderDashboard()

    expect(screen.getByRole('status')).toHaveTextContent('Dashboard verileri yükleniyor...')
  })

  it('renders an API error and retries the dashboard request', () => {
    const retry = vi.fn()

    setAuthenticatedRole('ADMIN')
    mockedUseDashboardData.mockReturnValue(
      createDashboardState({
        error: new ApiError('Forbidden', 403),
        retry,
      }),
    )

    renderDashboard()

    expect(
      screen.getByRole('heading', {
        name: 'Bu verilere erişim yetkiniz yok',
      }),
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Yeniden dene' }))

    expect(retry).toHaveBeenCalledTimes(1)
  })
  it('renders the cameras returned by the authorized backend request', () => {
    setAuthenticatedRole('OHS_SPECIALIST')
    mockedUseDashboardData.mockReturnValue(
      createDashboardState({
        cameras: [camera],
      }),
    )

    renderDashboard()

    expect(screen.getByRole('heading', { name: 'Kamera durumları' })).toBeInTheDocument()
    expect(screen.getByText('1 kamera')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Montaj Kamera 1' })).toBeInTheDocument()
    expect(screen.getByText('CAM-001')).toBeInTheDocument()
    expect(screen.getByText('Çevrimiçi')).toBeInTheDocument()
  })

  it('renders an empty state when no authorized cameras are returned', () => {
    setAuthenticatedRole('OHS_SPECIALIST')
    mockedUseDashboardData.mockReturnValue(
      createDashboardState({
        cameras: [],
      }),
    )

    renderDashboard()

    expect(screen.getByRole('heading', { name: 'Kamera bulunamadı' })).toBeInTheDocument()
    expect(
      screen.getByText('Erişebildiğiniz departmanlarda gösterilecek kamera bulunmuyor.'),
    ).toBeInTheDocument()
  })
  it('uses the realtime violation when REST contains the same violation id', () => {
    setAuthenticatedRole('OHS_SPECIALIST')
    mockedUseDashboardData.mockReturnValue(
      createDashboardState({
        recentViolations: [recentViolation],
      }),
    )
    mockedUseRealtimeViolations.mockReturnValue({
      violations: [realtimeViolation],
      dismissViolation: vi.fn(),
    })

    renderDashboard()

    expect(screen.getByText('1 ihlal')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Eldiven eksik' })).toBeInTheDocument()
    expect(screen.getByText('Montaj')).toBeInTheDocument()
    expect(screen.getByText('Tamamlandı')).toBeInTheDocument()
    expect(screen.getByText('Hazır')).toBeInTheDocument()
    expect(screen.queryByText('Bölüm bilgisi bekleniyor')).not.toBeInTheDocument()
  })

  it('forwards realtime dismissal to the existing realtime store hook', () => {
    const dismissViolation = vi.fn()

    setAuthenticatedRole('OHS_SPECIALIST')
    mockedUseDashboardData.mockReturnValue(createDashboardState())
    mockedUseRealtimeViolations.mockReturnValue({
      violations: [realtimeViolation],
      dismissViolation,
    })

    renderDashboard()

    fireEvent.click(screen.getByRole('button', { name: 'Uyarıyı kapat' }))

    expect(dismissViolation).toHaveBeenCalledWith('violation-1')
  })

  it('renders an empty state when REST and realtime contain no violations', () => {
    setAuthenticatedRole('OHS_SPECIALIST')
    mockedUseDashboardData.mockReturnValue(
      createDashboardState({
        recentViolations: [],
      }),
    )

    renderDashboard()

    expect(screen.getByRole('heading', { name: 'İhlal bulunamadı' })).toBeInTheDocument()
    expect(
      screen.getByText('Erişebildiğiniz departmanlarda gösterilecek ihlal bulunmuyor.'),
    ).toBeInTheDocument()
  })
})
