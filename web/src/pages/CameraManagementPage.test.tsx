import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import * as cameraManagementHook from '../features/admin/useCameraManagement'
import CameraManagementPage from './CameraManagementPage'
import * as cameraService from '../services/cameraService'
import * as departmentHook from '../features/admin/useAdminDepartmentOptions'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <CameraManagementPage />
    </MemoryRouter>,
  )
}

describe('CameraManagementPage', () => {
  it('renders the loading state', () => {
    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [],
      isLoading: true,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(
      screen.getByRole('status', {
        name: 'Kameralar yükleniyor',
      }),
    ).toBeInTheDocument()
  })

  it('renders the empty state', () => {
    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(
      screen.getByRole('heading', {
        name: 'Kamera bulunamadı',
      }),
    ).toBeInTheDocument()
  })

  it('renders camera data', () => {
    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          name: 'Kamera 1',
          code: 'CAM-001',
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Kaynak',
          active: true,
          connectionStatus: 'ONLINE',
          lastSeenAt: '2026-08-19T10:00:00Z',
          activeSessionId: null,
        },
      ],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('Kamera 1')).toBeInTheDocument()
    expect(screen.getByText('CAM-001')).toBeInTheDocument()
    expect(screen.getByText('Kaynak')).toBeInTheDocument()
    expect(screen.getByText('Çevrimiçi')).toBeInTheDocument()
    expect(screen.getByText('Aktif')).toBeInTheDocument()
  })

  it('renders an error state and retries', () => {
    const retry = vi.fn()

    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: new Error('camera load failed'),
      retry,
    })

    renderPage()

    expect(
      screen.getByRole('heading', {
        name: 'Kameralar yüklenemedi',
      }),
    ).toBeInTheDocument()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Tekrar dene',
      }),
    )

    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('creates a camera and refreshes the list', async () => {
    const retry = vi.fn()

    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry,
    })

    vi.spyOn(departmentHook, 'useAdminDepartmentOptions').mockReturnValue({
      departments: [
        {
          id: '22222222-2222-2222-2222-222222222222',
          name: 'Kaynak',
        },
      ],
      isLoading: false,
      error: null,
    })

    const createSpy = vi.spyOn(cameraService, 'createCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'OFFLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })
    renderPage()
    fireEvent.click(
      screen.getByRole('button', {
        name: 'Yeni Kamera',
      }),
    )

    fireEvent.change(screen.getByLabelText('Kamera adı'), {
      target: { value: 'Kamera 1' },
    })

    fireEvent.change(screen.getByLabelText('Kamera kodu'), {
      target: { value: 'CAM-001' },
    })

    fireEvent.change(screen.getByLabelText('Departman'), {
      target: {
        value: '22222222-2222-2222-2222-222222222222',
      },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kamera ekle',
      }),
    )

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        name: 'Kamera 1',
        code: 'CAM-001',
        departmentId: '22222222-2222-2222-2222-222222222222',
      })
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('updates a camera and refreshes the list', async () => {
    const retry = vi.fn()

    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          name: 'Kamera 1',
          code: 'CAM-001',
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Kaynak',
          active: true,
          connectionStatus: 'ONLINE',
          lastSeenAt: null,
          activeSessionId: null,
        },
      ],
      isLoading: false,
      error: null,
      retry,
    })

    vi.spyOn(departmentHook, 'useAdminDepartmentOptions').mockReturnValue({
      departments: [
        {
          id: '22222222-2222-2222-2222-222222222222',
          name: 'Kaynak',
        },
      ],
      isLoading: false,
      error: null,
    })

    const updateSpy = vi.spyOn(cameraService, 'updateCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1 Güncel',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Düzenle',
      }),
    )

    fireEvent.change(screen.getByLabelText('Kamera adı'), {
      target: {
        value: 'Kamera 1 Güncel',
      },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Değişiklikleri kaydet',
      }),
    )

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        name: 'Kamera 1 Güncel',
        code: 'CAM-001',
        departmentId: '22222222-2222-2222-2222-222222222222',
      })
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('deactivates an active camera after confirmation', async () => {
    const retry = vi.fn()

    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          name: 'Kamera 1',
          code: 'CAM-001',
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Kaynak',
          active: true,
          connectionStatus: 'ONLINE',
          lastSeenAt: null,
          activeSessionId: null,
        },
      ],
      isLoading: false,
      error: null,
      retry,
    })

    vi.spyOn(departmentHook, 'useAdminDepartmentOptions').mockReturnValue({
      departments: [],
      isLoading: false,
      error: null,
    })

    const updateSpy = vi.spyOn(cameraService, 'updateCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: false,
      connectionStatus: 'OFFLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Pasife al' }))

    const dialog = screen.getByRole('dialog', {
      name: 'Kamerayı pasife al',
    })

    expect(dialog).toBeInTheDocument()

    fireEvent.click(
      within(dialog).getByRole('button', {
        name: 'Pasife al',
      }),
    )

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        active: false,
      })
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('reactivates an inactive camera after confirmation', async () => {
    const retry = vi.fn()

    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          name: 'Kamera 1',
          code: 'CAM-001',
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Kaynak',
          active: false,
          connectionStatus: 'OFFLINE',
          lastSeenAt: null,
          activeSessionId: null,
        },
      ],
      isLoading: false,
      error: null,
      retry,
    })

    vi.spyOn(departmentHook, 'useAdminDepartmentOptions').mockReturnValue({
      departments: [],
      isLoading: false,
      error: null,
    })

    const updateSpy = vi.spyOn(cameraService, 'updateCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'OFFLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Aktifleştir',
      }),
    )

    const dialog = screen.getByRole('dialog', {
      name: 'Kamerayı aktifleştir',
    })

    expect(dialog).toBeInTheDocument()

    fireEvent.click(
      within(dialog).getByRole('button', {
        name: 'Aktifleştir',
      }),
    )

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        active: true,
      })
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('renders the restricted zone action for a camera', () => {
    vi.spyOn(cameraManagementHook, 'useCameraManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          name: 'Kamera 1',
          code: 'CAM-001',
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Kaynak',
          active: true,
          connectionStatus: 'ONLINE',
          lastSeenAt: null,
          activeSessionId: null,
        },
      ],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage()
    expect(
      screen.getByRole('button', {
        name: 'Yasaklı Alan',
      }),
    ).toBeInTheDocument()
  })
})
