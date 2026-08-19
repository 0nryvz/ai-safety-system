import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CameraForm from './CameraForm'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const departments = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Kaynak',
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Montaj',
  },
]

describe('CameraForm', () => {
  it('shows validation errors for empty required fields', () => {
    render(<CameraForm departments={departments} onSubmit={vi.fn()} onCancel={vi.fn()} />)

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kamera ekle',
      }),
    )

    expect(screen.getByText('Kamera adı zorunludur.')).toBeInTheDocument()
    expect(screen.getByText('Kamera kodu zorunludur.')).toBeInTheDocument()
    expect(screen.getByText('Departman seçimi zorunludur.')).toBeInTheDocument()
  })

  it('submits camera values in create mode', () => {
    const onSubmit = vi.fn()

    render(<CameraForm departments={departments} onSubmit={onSubmit} onCancel={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Kamera adı'), {
      target: { value: 'Kamera 1' },
    })

    fireEvent.change(screen.getByLabelText('Kamera kodu'), {
      target: { value: 'CAM-001' },
    })

    fireEvent.change(screen.getByLabelText('Departman'), {
      target: { value: departments[0].id },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kamera ekle',
      }),
    )

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: departments[0].id,
    })
  })

  it('loads camera values in edit mode', () => {
    render(
      <CameraForm
        camera={{
          id: '33333333-3333-3333-3333-333333333333',
          name: 'Kamera 2',
          code: 'CAM-002',
          departmentId: departments[1].id,
          departmentName: 'Montaj',
          active: true,
          connectionStatus: 'ONLINE',
          lastSeenAt: null,
          activeSessionId: null,
        }}
        departments={departments}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Kamera adı')).toHaveValue('Kamera 2')
    expect(screen.getByLabelText('Kamera kodu')).toHaveValue('CAM-002')
    expect(screen.getByLabelText('Departman')).toHaveValue(departments[1].id)

    expect(
      screen.getByRole('button', {
        name: 'Değişiklikleri kaydet',
      }),
    ).toBeInTheDocument()
  })

  it('calls onCancel', () => {
    const onCancel = vi.fn()

    render(<CameraForm departments={departments} onSubmit={vi.fn()} onCancel={onCancel} />)

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Vazgeç',
      }),
    )

    expect(onCancel).toHaveBeenCalledTimes(1)
  })
  it('shows server field errors', () => {
    render(
      <CameraForm
        departments={[
          {
            id: '22222222-2222-2222-2222-222222222222',
            name: 'Üretim',
          },
        ]}
        isSubmitting={false}
        serverErrors={{
          name: 'Kamera adı zaten kullanılıyor.',
          code: 'Kamera kodu zaten kullanılıyor.',
          departmentId: 'Geçerli bir departman seçin.',
        }}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByText('Kamera adı zaten kullanılıyor.')).toBeInTheDocument()

    expect(screen.getByText('Kamera kodu zaten kullanılıyor.')).toBeInTheDocument()

    expect(screen.getByText('Geçerli bir departman seçin.')).toBeInTheDocument()
  })
})
