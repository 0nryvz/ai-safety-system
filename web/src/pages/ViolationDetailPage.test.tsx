import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import * as detailHook from '../features/violations/useViolationDetail'
import * as violationService from '../services/violationService'
import ViolationDetailPage from './ViolationDetailPage'
import * as realtimeHook from '../core/realtime/useRealtimeViolations'
import { ApiError } from '../core/api/apiError'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/violations/11111111-1111-1111-1111-111111111111']}>
      <Routes>
        <Route path="/violations/:id" element={<ViolationDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

const violation = {
  violationId: '11111111-1111-1111-1111-111111111111',
  cameraId: '22222222-2222-2222-2222-222222222222',
  cameraName: 'Kaynak Kamera 1',
  cameraCode: 'CAM-001',
  departmentId: '33333333-3333-3333-3333-333333333333',
  departmentName: 'Kaynak',
  sessionId: '44444444-4444-4444-4444-444444444444',
  type: 'MISSING_GLOVES' as const,
  confidence: 0.94,
  modelVersion: 'model-v1',
  detectedAt: '2026-08-19T10:00:00Z',
  startedAt: '2026-08-19T10:00:00Z',
  endedAt: null,
  lifecycleStatus: 'ACTIVE' as const,
  reviewStatus: 'UNREVIEWED' as const,
  reviewedBy: null,
  reviewedAt: null,
  recordingStatus: 'PROCESSING' as const,
  clipReady: false,
  playbackUrl: null,
  coverImageKey: null,
  coverImageReady: false,
  version: 3,
}

describe('ViolationDetailPage', () => {
  it('renders violation detail information', () => {
    vi.spyOn(detailHook, 'useViolationDetail').mockReturnValue({
      data: violation,
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByRole('heading', { name: 'İhlal Detayı' })).toBeInTheDocument()
    expect(screen.getByText('Kaynak Kamera 1 (CAM-001)')).toBeInTheDocument()
    expect(screen.getByText('Kaynak')).toBeInTheDocument()
    expect(screen.getByText('%94')).toBeInTheDocument()

    expect(screen.getByText('Video işleniyor')).toBeInTheDocument()
    expect(screen.getByText('Video oynatılmaya hazır hale getiriliyor.')).toBeInTheDocument()
  })

  it('submits the selected review status after confirmation', async () => {
    const retry = vi.fn()

    vi.spyOn(detailHook, 'useViolationDetail').mockReturnValue({
      data: violation,
      isLoading: false,
      error: null,
      retry,
    })

    const reviewSpy = vi.spyOn(violationService, 'reviewViolation').mockResolvedValue({
      violationId: violation.violationId,
      reviewStatus: 'CONFIRMED',
      reviewedBy: '55555555-5555-5555-5555-555555555555',
      reviewedAt: '2026-08-19T10:05:00Z',
      version: 4,
    })

    renderPage()

    fireEvent.change(screen.getByLabelText('İnceleme sonucu'), {
      target: { value: 'CONFIRMED' },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'İncelemeyi kaydet',
      }),
    )

    expect(
      screen.getByRole('heading', {
        name: 'İnceleme durumunu güncelle',
      }),
    ).toBeInTheDocument()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Onayla',
      }),
    )

    await waitFor(() => {
      expect(reviewSpy).toHaveBeenCalledWith(violation.violationId, 'CONFIRMED', violation.version)
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('shows an error when review submission fails', async () => {
    vi.spyOn(detailHook, 'useViolationDetail').mockReturnValue({
      data: violation,
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    vi.spyOn(violationService, 'reviewViolation').mockRejectedValue(new Error('review failed'))

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'İncelemeyi kaydet',
      }),
    )

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Onayla',
      }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'İnceleme durumu güncellenemedi. Lütfen tekrar deneyin.',
    )
  })

  it('refreshes detail once when realtime state changes for the same violation', async () => {
    const retry = vi.fn()

    vi.spyOn(detailHook, 'useViolationDetail').mockReturnValue({
      data: violation,
      isLoading: false,
      error: null,
      retry,
    })

    vi.spyOn(realtimeHook, 'useRealtimeViolations').mockReturnValue({
      violations: [
        {
          violationId: violation.violationId,
          type: 'MISSING_GLOVES',
          cameraName: 'Kaynak Kamera 1',
          departmentName: 'Kaynak',
          startedAt: '2026-08-19T10:00:00Z',
          confidence: 0.94,
          lifecycleStatus: 'ACTIVE',
          recordingStatus: 'READY',
          clipReady: true,
          coverImageReady: false,
          lastEventAt: '2026-08-19T10:05:00Z',
          dismissed: false,
          errorCode: null,
        },
      ],
      dismissViolation: vi.fn(),
    })

    const { rerender } = renderPage()

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })

    rerender(
      <MemoryRouter initialEntries={['/violations/11111111-1111-1111-1111-111111111111']}>
        <Routes>
          <Route path="/violations/:id" element={<ViolationDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('refreshes detail and shows a conflict message when the review version is stale', async () => {
    const retry = vi.fn()

    vi.spyOn(detailHook, 'useViolationDetail').mockReturnValue({
      data: violation,
      isLoading: false,
      error: null,
      retry,
    })

    vi.spyOn(violationService, 'reviewViolation').mockRejectedValue(
      new ApiError('Violation version conflict', 409, {
        status: 409,
        code: 'VIOLATION_VERSION_CONFLICT',
        message: 'Violation version conflict',
      }),
    )

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'İncelemeyi kaydet',
      }),
    )

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Onayla',
      }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'İhlal başka bir kullanıcı tarafından güncellendi. Güncel bilgileri yükleyip tekrar deneyin.',
    )

    expect(retry).toHaveBeenCalledTimes(1)
  })
})

it('refreshes detail when the cover image becomes ready in realtime', async () => {
  const retry = vi.fn()

  vi.spyOn(detailHook, 'useViolationDetail').mockReturnValue({
    data: violation,
    isLoading: false,
    error: null,
    retry,
  })

  vi.spyOn(realtimeHook, 'useRealtimeViolations').mockReturnValue({
    violations: [
      {
        violationId: violation.violationId,
        type: 'MISSING_GLOVES',
        cameraName: 'Kaynak Kamera 1',
        departmentName: 'Kaynak',
        startedAt: '2026-08-19T10:00:00Z',
        confidence: 0.94,
        lifecycleStatus: 'ACTIVE',
        recordingStatus: 'PROCESSING',
        clipReady: false,
        coverImageReady: true,
        lastEventAt: '2026-08-19T10:05:00Z',
        dismissed: false,
        errorCode: null,
      },
    ],
    dismissViolation: vi.fn(),
  })

  renderPage()

  await waitFor(() => {
    expect(retry).toHaveBeenCalledTimes(1)
  })
})

it('renders the violation cover image when the cover is ready', async () => {
  vi.spyOn(detailHook, 'useViolationDetail').mockReturnValue({
    data: {
      ...violation,
      coverImageKey: 'violations/2026/08/test/cover.jpg',
      coverImageReady: true,
    },
    isLoading: false,
    error: null,
    retry: vi.fn(),
  })

  vi.spyOn(violationService, 'getViolationCoverUrl').mockResolvedValue({
    url: 'https://media.example.test/authorized-cover',
    expiresAt: '2099-08-18T10:05:00Z',
  })

  renderPage()

  expect(screen.getByRole('heading', { name: 'İhlal Kapak Görseli' })).toBeInTheDocument()

  const image = await screen.findByAltText('İhlal kapak görseli')

  expect(image).toHaveAttribute('src', 'https://media.example.test/authorized-cover')
})
