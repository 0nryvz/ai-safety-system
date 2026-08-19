import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import * as cameraOptions from '../features/violations/useCameraOptions'
import * as departmentOptions from '../features/violations/useDepartmentOptions'
import * as violationHistory from '../features/violations/useViolationHistory'
import type { ViolationListItem } from '../services/violationService'
import ViolationHistoryPage from './ViolationHistoryPage'
import { ApiError } from '../core/api/apiError'

const violation: ViolationListItem = {
  violationId: '11111111-1111-1111-1111-111111111111',
  cameraId: '22222222-2222-2222-2222-222222222222',
  departmentId: '33333333-3333-3333-3333-333333333333',
  type: 'MISSING_GLOVES',
  startedAt: '2026-08-18T12:00:00Z',
  endedAt: null,
  confidence: 0.92,
  lifecycleStatus: 'COMPLETED',
  reviewStatus: 'UNREVIEWED',
}

beforeEach(() => {
  vi.spyOn(cameraOptions, 'useCameraOptions').mockReturnValue({
    cameras: [
      {
        id: violation.cameraId,
        name: 'Kaynak Kamera 1',
        code: 'CAM-001',
        departmentId: violation.departmentId,
        active: true,
        connectionStatus: 'ONLINE',
        lastSeenAt: '2026-08-18T12:00:00Z',
        activeSessionId: null,
        departmentName: 'Kaynak',
      },
    ],
    isLoading: false,
    error: null,
  })

  vi.spyOn(departmentOptions, 'useDepartmentOptions').mockReturnValue({
    departments: [
      {
        id: violation.departmentId,
        name: 'Kaynak',
      },
    ],
    isLoading: false,
    error: null,
  })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPage(initialEntry = '/violations') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <ViolationHistoryPage />
    </MemoryRouter>,
  )
}

describe('ViolationHistoryPage', () => {
  it('renders violation history filters', () => {
    vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByLabelText('Kamera')).toBeInTheDocument()
    expect(screen.getByLabelText('Departman')).toBeInTheDocument()
    expect(screen.getByLabelText('İhlal tipi')).toBeInTheDocument()
    expect(screen.getByLabelText('İhlal durumu')).toBeInTheDocument()
    expect(screen.getByLabelText('İnceleme durumu')).toBeInTheDocument()
    expect(screen.getByLabelText('Sıralama')).toBeInTheDocument()
  })

  it('renders violation data with camera and department names', () => {
    vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: {
        content: [violation],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    const table = screen.getByRole('table')

    expect(within(table).getByText('Eldiven eksik')).toBeInTheDocument()
    expect(within(table).getByText('Kaynak Kamera 1 (CAM-001)')).toBeInTheDocument()
    expect(within(table).getByText('Kaynak')).toBeInTheDocument()
    expect(within(table).getByText('%92')).toBeInTheDocument()
  })

  it('renders the empty state when no violations exist', () => {
    vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('İhlal bulunamadı')).toBeInTheDocument()
  })

  it('updates the violation query when department filter changes', async () => {
    const historySpy = vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    fireEvent.change(screen.getByLabelText('Departman'), {
      target: {
        value: violation.departmentId,
      },
    })

    await waitFor(() => {
      expect(historySpy).toHaveBeenCalledWith(
        expect.objectContaining({
          departmentId: violation.departmentId,
          page: 0,
        }),
      )
    })
  })
  it('updates the violation query when sorting changes', async () => {
    const historySpy = vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    fireEvent.change(screen.getByLabelText('Sıralama'), {
      target: {
        value: 'confidence,desc',
      },
    })

    await waitFor(() => {
      expect(historySpy).toHaveBeenCalledWith(
        expect.objectContaining({
          sort: ['confidence,desc'],
          page: 0,
        }),
      )
    })
  })
  it('renders the loading state while violation history is loading', () => {
    vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: null,
      isLoading: true,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(
      screen.getByRole('status', {
        name: 'İhlal geçmişi yükleniyor',
      }),
    ).toBeInTheDocument()
  })

  it('shows a network-specific error state', () => {
    vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: null,
      isLoading: false,
      error: new ApiError('Sunucuya bağlanılamadı.', 0),
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('Sunucuya bağlanılamadı')).toBeInTheDocument()
    expect(screen.getByText('Bağlantınızı kontrol edip tekrar deneyin.')).toBeInTheDocument()
  })

  it('shows a validation-specific error state for client errors', () => {
    vi.spyOn(violationHistory, 'useViolationHistory').mockReturnValue({
      data: null,
      isLoading: false,
      error: new ApiError('Invalid query', 400),
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('Filtreler işlenemedi')).toBeInTheDocument()
    expect(
      screen.getByText('Seçili filtreleri ve tarih aralığını kontrol edip tekrar deneyin.'),
    ).toBeInTheDocument()
  })
})
