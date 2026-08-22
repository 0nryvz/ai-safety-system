import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as departmentManagementHook from '../features/admin/useDepartmentManagement'
import * as departmentService from '../services/departmentService'
import DepartmentManagementPage from './DepartmentManagementPage'
import { AxiosError, AxiosHeaders } from 'axios'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <DepartmentManagementPage />
    </MemoryRouter>,
  )
}

describe('DepartmentManagementPage', () => {
  it('renders the loading state', () => {
    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [],
      isLoading: true,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(
      screen.getByRole('status', {
        name: 'Departmanlar yükleniyor',
      }),
    ).toBeInTheDocument()
  })

  it('renders the error state and retries', () => {
    const retry = vi.fn()

    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: new Error('request failed'),
      retry,
    })

    renderPage()

    expect(screen.getByText('Departmanlar yüklenemedi')).toBeInTheDocument()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Tekrar dene',
      }),
    )

    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('renders the empty state', () => {
    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('Departman bulunamadı')).toBeInTheDocument()
  })

  it('renders departments', () => {
    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          code: 'URETIM',
          name: 'Üretim',
          description: 'Üretim departmanı',
          active: true,
        },
      ],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage()

    expect(screen.getByText('Üretim')).toBeInTheDocument()
    expect(screen.getByText('URETIM')).toBeInTheDocument()
    expect(screen.getByText('Üretim departmanı')).toBeInTheDocument()
    expect(screen.getByText('Aktif')).toBeInTheDocument()
  })

  it('creates a department and refreshes the list', async () => {
    const retry = vi.fn()

    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry,
    })

    const createSpy = vi.spyOn(departmentService, 'createDepartment').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      code: 'URETIM',
      name: 'Üretim',
      description: 'Üretim departmanı',
      active: true,
    })

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Yeni Departman',
      }),
    )

    fireEvent.change(screen.getByLabelText('Departman kodu'), {
      target: { value: ' uretim ' },
    })

    fireEvent.change(screen.getByLabelText('Departman adı'), {
      target: { value: ' Üretim ' },
    })

    fireEvent.change(screen.getByLabelText('Açıklama'), {
      target: { value: ' Üretim departmanı ' },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Departman ekle',
      }),
    )

    await vi.waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        code: 'uretim',
        name: 'Üretim',
        description: 'Üretim departmanı',
      })
    })

    await vi.waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })

    expect(
      screen.queryByRole('button', {
        name: 'Departman ekle',
      }),
    ).not.toBeInTheDocument()
  })

  it('updates a department and refreshes the list', async () => {
    const retry = vi.fn()

    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          code: 'URETIM',
          name: 'Üretim',
          description: 'Eski açıklama',
          active: true,
        },
      ],
      isLoading: false,
      error: null,
      retry,
    })

    const updateSpy = vi.spyOn(departmentService, 'updateDepartment').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      code: 'URETIM',
      name: 'Yeni Üretim',
      description: 'Yeni açıklama',
      active: true,
    })

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Düzenle',
      }),
    )

    expect(screen.getByLabelText('Departman kodu')).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Departman adı'), {
      target: { value: ' Yeni Üretim ' },
    })

    fireEvent.change(screen.getByLabelText('Açıklama'), {
      target: { value: ' Yeni açıklama ' },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Değişiklikleri kaydet',
      }),
    )

    await vi.waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        name: 'Yeni Üretim',
        description: 'Yeni açıklama',
      })
    })

    await vi.waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('toggles department active status and refreshes the list', async () => {
    const retry = vi.fn()

    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          code: 'URETIM',
          name: 'Üretim',
          description: 'Üretim departmanı',
          active: true,
        },
      ],
      isLoading: false,
      error: null,
      retry,
    })

    const updateSpy = vi.spyOn(departmentService, 'updateDepartment').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      code: 'URETIM',
      name: 'Üretim',
      description: 'Üretim departmanı',
      active: false,
    })

    renderPage()

    // Tablodaki "Pasife al" butonuna tıkla.
    fireEvent.click(
      screen.getByRole('button', {
        name: 'Pasife al',
      }),
    )

    // Confirm dialog açılmış olmalı.
    const dialog = screen.getByRole('dialog', {
      name: 'Departmanı pasife al',
    })

    expect(dialog).toBeInTheDocument()

    expect(
      within(dialog).getByText('Bu departman pasife alınacak. Devam etmek istiyor musunuz?'),
    ).toBeInTheDocument()

    // Yalnızca dialog içindeki confirm butonuna tıkla.
    fireEvent.click(
      within(dialog).getByRole('button', {
        name: 'Pasife al',
      }),
    )

    await vi.waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        active: false,
      })
    })

    await vi.waitFor(() => {
      expect(retry).toHaveBeenCalledTimes(1)
    })
  })

  it('shows duplicate department error returned by backend', async () => {
    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    vi.spyOn(departmentService, 'createDepartment').mockRejectedValue(
      new AxiosError('Conflict', '409', undefined, undefined, {
        status: 409,
        statusText: 'Conflict',
        headers: {},
        config: {
          headers: new AxiosHeaders(),
        },
        data: {
          status: 409,
          message: 'Department code already exists.',
        },
      }),
    )

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Yeni Departman',
      }),
    )

    fireEvent.change(screen.getByLabelText('Departman kodu'), {
      target: { value: 'URETIM' },
    })

    fireEvent.change(screen.getByLabelText('Departman adı'), {
      target: { value: 'Üretim' },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Departman ekle',
      }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Department code already exists.')
  })

  it('shows backend field validation errors in create form', async () => {
    vi.spyOn(departmentManagementHook, 'useDepartmentManagement').mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      retry: vi.fn(),
    })

    vi.spyOn(departmentService, 'createDepartment').mockRejectedValue(
      new AxiosError('Bad Request', '400', undefined, undefined, {
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config: {
          headers: new AxiosHeaders(),
        },
        data: {
          status: 400,
          message: 'Validation failed.',
          fieldErrors: {
            code: 'Code is invalid.',
            name: 'Name is invalid.',
          },
        },
      }),
    )

    renderPage()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Yeni Departman',
      }),
    )

    fireEvent.change(screen.getByLabelText('Departman kodu'), {
      target: { value: 'URETIM' },
    })

    fireEvent.change(screen.getByLabelText('Departman adı'), {
      target: { value: 'Üretim' },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Departman ekle',
      }),
    )

    expect(await screen.findByText('Code is invalid.')).toBeInTheDocument()

    expect(screen.getByText('Name is invalid.')).toBeInTheDocument()
  })
})
