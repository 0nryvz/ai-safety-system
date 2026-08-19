import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as userManagementHook from '../features/admin/useUserManagement'
import UserManagementPage from './UserManagementPage'
import * as departmentHook from '../features/admin/useAdminDepartmentOptions'
import * as userService from '../services/userService'

vi.mock('../features/realtime/AlertCenter', () => ({
  default: () => null,
}))

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <UserManagementPage />
    </MemoryRouter>,
  )
}

describe('UserManagementPage', () => {
  it('renders the loading state', () => {
    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [],
      isLoading: true,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(
      screen.getByRole('status', {
        name: 'Kullanıcılar yükleniyor',
      }),
    ).toBeInTheDocument()
  })

  it('renders the empty state', () => {
    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('Kullanıcı bulunamadı')).toBeInTheDocument()
  })

  it('renders user data', () => {
    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          email: 'user@example.com',
          fullName: 'Test User',
          active: true,
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Üretim',
          roles: ['OHS_SPECIALIST'],
          departmentIds: ['22222222-2222-2222-2222-222222222222'],
          createdAt: '2026-08-15T00:00:00Z',
        },
      ],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('Test User')).toBeInTheDocument()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByText('İSG Uzmanı')).toBeInTheDocument()
    expect(screen.getByText('Üretim')).toBeInTheDocument()
    expect(screen.getByText('Aktif')).toBeInTheDocument()
  })

  it('renders an error state and retries', () => {
    const retry = vi.fn()

    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: new Error('request failed'),
      retry,
    })

    renderPage()

    expect(screen.getByText('Kullanıcılar yüklenemedi')).toBeInTheDocument()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Tekrar dene',
      }),
    )

    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('creates a user and refreshes the list', async () => {
    const retry = vi.fn()

    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry,
    })

    vi.spyOn(departmentHook, 'useAdminDepartmentOptions').mockReturnValue({
      departments: [
        {
          id: '22222222-2222-2222-2222-222222222222',
          name: 'Üretim',
        },
      ],
      isLoading: false,
      error: null,
    })

    const createSpy = vi.spyOn(userService, 'createUser').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      fullName: 'Test User',
      active: true,
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Üretim',
      roles: ['OHS_SPECIALIST'],
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      createdAt: '2026-08-19T00:00:00Z',
    })

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Yeni Kullanıcı',
      }),
    )

    fireEvent.change(screen.getByLabelText('Ad soyad'), {
      target: {
        value: 'Test User',
      },
    })

    fireEvent.change(screen.getByLabelText('E-posta'), {
      target: {
        value: 'user@example.com',
      },
    })

    fireEvent.change(screen.getByLabelText('Parola'), {
      target: {
        value: '123456',
      },
    })

    fireEvent.click(
      screen.getByRole('checkbox', {
        name: 'İSG Uzmanı',
      }),
    )

    fireEvent.click(
      screen.getByRole('checkbox', {
        name: 'Üretim',
      }),
    )

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kullanıcı oluştur',
      }),
    )

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        fullName: 'Test User',
        email: 'user@example.com',
        password: '123456',
        departmentIds: ['22222222-2222-2222-2222-222222222222'],
        roleNames: ['OHS_SPECIALIST'],
      })
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('updates a user and refreshes the list', async () => {
    const retry = vi.fn()

    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          email: 'user@example.com',
          fullName: 'Test User',
          active: true,
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Üretim',
          roles: ['OHS_SPECIALIST'],
          departmentIds: ['22222222-2222-2222-2222-222222222222'],
          createdAt: '2026-08-15T00:00:00Z',
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
          name: 'Üretim',
        },
        {
          id: '33333333-3333-3333-3333-333333333333',
          name: 'Kaynak',
        },
      ],
      isLoading: false,
      error: null,
    })

    const updateSpy = vi.spyOn(userService, 'updateUser').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      fullName: 'Updated User',
      active: true,
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Üretim',
      roles: ['OHS_SPECIALIST', 'SHIFT_SUPERVISOR'],
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      createdAt: '2026-08-15T00:00:00Z',
    })

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Düzenle',
      }),
    )

    fireEvent.change(screen.getByLabelText('Ad soyad'), {
      target: {
        value: 'Updated User',
      },
    })

    fireEvent.click(
      screen.getByRole('checkbox', {
        name: 'Vardiya Sorumlusu',
      }),
    )

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Değişiklikleri kaydet',
      }),
    )

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        fullName: 'Updated User',
        departmentIds: ['22222222-2222-2222-2222-222222222222'],
        roleNames: ['OHS_SPECIALIST', 'SHIFT_SUPERVISOR'],
        active: true,
      })
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })
  it('deactivates a user after confirmation', async () => {
    const retry = vi.fn()

    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          email: 'user@example.com',
          fullName: 'Test User',
          active: true,
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Üretim',
          roles: ['OHS_SPECIALIST'],
          departmentIds: ['22222222-2222-2222-2222-222222222222'],
          createdAt: '2026-08-15T00:00:00Z',
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

    const deactivateSpy = vi.spyOn(userService, 'deactivateUser').mockResolvedValue()

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Pasife al',
      }),
    )

    const dialog = screen.getByRole('dialog', {
      name: 'Kullanıcıyı pasife al',
    })

    expect(dialog).toBeInTheDocument()

    fireEvent.click(
      within(dialog).getByRole('button', {
        name: 'Pasife al',
      }),
    )

    await waitFor(() => {
      expect(deactivateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111')
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('reactivates an inactive user after confirmation', async () => {
    const retry = vi.fn()

    vi.spyOn(userManagementHook, 'useUserManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          email: 'user@example.com',
          fullName: 'Test User',
          active: false,
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Üretim',
          roles: ['OHS_SPECIALIST'],
          departmentIds: ['22222222-2222-2222-2222-222222222222'],
          createdAt: '2026-08-15T00:00:00Z',
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

    const updateSpy = vi.spyOn(userService, 'updateUser').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      fullName: 'Test User',
      active: true,
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Üretim',
      roles: ['OHS_SPECIALIST'],
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      createdAt: '2026-08-15T00:00:00Z',
    })

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Aktifleştir',
      }),
    )

    const dialog = screen.getByRole('dialog', {
      name: 'Kullanıcıyı aktifleştir',
    })

    expect(dialog).toBeInTheDocument()

    fireEvent.click(
      within(dialog).getByRole('button', {
        name: 'Aktifleştir',
      }),
    )

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        fullName: 'Test User',
        departmentIds: ['22222222-2222-2222-2222-222222222222'],
        roleNames: ['OHS_SPECIALIST'],
        active: true,
      })
    })

    await waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })
})
