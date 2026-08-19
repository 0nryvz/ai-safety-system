import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UserForm from './UserForm'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const departments = [
  {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Üretim',
  },
  {
    id: '33333333-3333-3333-3333-333333333333',
    name: 'Kaynak',
  },
]

describe('UserForm', () => {
  it('shows validation errors when create fields are empty', () => {
    const onSubmit = vi.fn()

    render(
      <UserForm
        departments={departments}
        isSubmitting={false}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    )

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kullanıcı oluştur',
      }),
    )

    expect(screen.getByText('Ad soyad zorunludur.')).toBeInTheDocument()
    expect(screen.getByText('E-posta zorunludur.')).toBeInTheDocument()
    expect(screen.getByText('Parola zorunludur.')).toBeInTheDocument()
    expect(screen.getByText('En az bir departman seçin.')).toBeInTheDocument()
    expect(screen.getByText('En az bir rol seçin.')).toBeInTheDocument()

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits create values', () => {
    const onSubmit = vi.fn()

    render(
      <UserForm
        departments={departments}
        isSubmitting={false}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
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

    expect(onSubmit).toHaveBeenCalledWith({
      fullName: 'Test User',
      email: 'user@example.com',
      password: '123456',
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      roleNames: ['OHS_SPECIALIST'],
    })
  })

  it('loads edit values without email and password inputs', () => {
    render(
      <UserForm
        user={{
          id: '11111111-1111-1111-1111-111111111111',
          email: 'user@example.com',
          fullName: 'Test User',
          active: true,
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Üretim',
          roles: ['OHS_SPECIALIST'],
          departmentIds: ['22222222-2222-2222-2222-222222222222'],
          createdAt: '2026-08-15T00:00:00Z',
        }}
        departments={departments}
        isSubmitting={false}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Ad soyad')).toHaveValue('Test User')

    expect(screen.queryByLabelText('E-posta')).not.toBeInTheDocument()

    expect(screen.queryByLabelText('Parola')).not.toBeInTheDocument()

    expect(
      screen.getByRole('checkbox', {
        name: 'İSG Uzmanı',
      }),
    ).toBeChecked()

    expect(
      screen.getByRole('checkbox', {
        name: 'Üretim',
      }),
    ).toBeChecked()
  })

  it('submits edited values', () => {
    const onSubmit = vi.fn()

    render(
      <UserForm
        user={{
          id: '11111111-1111-1111-1111-111111111111',
          email: 'user@example.com',
          fullName: 'Test User',
          active: true,
          departmentId: '22222222-2222-2222-2222-222222222222',
          departmentName: 'Üretim',
          roles: ['OHS_SPECIALIST'],
          departmentIds: ['22222222-2222-2222-2222-222222222222'],
          createdAt: '2026-08-15T00:00:00Z',
        }}
        departments={departments}
        isSubmitting={false}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
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

    expect(onSubmit).toHaveBeenCalledWith({
      fullName: 'Updated User',
      email: 'user@example.com',
      password: '',
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      roleNames: ['OHS_SPECIALIST', 'SHIFT_SUPERVISOR'],
    })
  })

  it('calls onCancel', () => {
    const onCancel = vi.fn()

    render(
      <UserForm
        departments={departments}
        isSubmitting={false}
        onSubmit={vi.fn()}
        onCancel={onCancel}
      />,
    )

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Vazgeç',
      }),
    )

    expect(onCancel).toHaveBeenCalledTimes(1)
  })
  it('shows server field errors', () => {
    render(
      <UserForm
        departments={departments}
        isSubmitting={false}
        serverErrors={{
          email: 'Bu e-posta zaten kullanılıyor.',
          roleNames: 'En az bir geçerli rol seçin.',
        }}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByText('Bu e-posta zaten kullanılıyor.')).toBeInTheDocument()

    expect(screen.getByText('En az bir geçerli rol seçin.')).toBeInTheDocument()
  })
})
