import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DepartmentForm from './DepartmentForm'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('DepartmentForm', () => {
  it('submits create values', () => {
    const onSubmit = vi.fn()

    render(<DepartmentForm onSubmit={onSubmit} onCancel={() => undefined} />)

    fireEvent.change(screen.getByLabelText('Departman kodu'), {
      target: { value: 'uretim' },
    })

    fireEvent.change(screen.getByLabelText('Departman adı'), {
      target: { value: 'Üretim' },
    })

    fireEvent.change(screen.getByLabelText('Açıklama'), {
      target: { value: 'Üretim departmanı' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Departman ekle' }))

    expect(onSubmit).toHaveBeenCalledWith({
      code: 'uretim',
      name: 'Üretim',
      description: 'Üretim departmanı',
    })
  })

  it('shows validation errors for empty create fields', () => {
    const onSubmit = vi.fn()

    render(<DepartmentForm onSubmit={onSubmit} onCancel={() => undefined} />)

    fireEvent.click(screen.getByRole('button', { name: 'Departman ekle' }))

    expect(screen.getByText('Departman kodu zorunludur.')).toBeInTheDocument()
    expect(screen.getByText('Departman adı zorunludur.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('disables code editing in edit mode', () => {
    render(
      <DepartmentForm
        department={{
          id: '11111111-1111-1111-1111-111111111111',
          code: 'URETIM',
          name: 'Üretim',
          description: 'Üretim departmanı',
          active: true,
        }}
        onSubmit={() => undefined}
        onCancel={() => undefined}
      />,
    )

    expect(screen.getByLabelText('Departman kodu')).toBeDisabled()
  })

  it('submits edited values', () => {
    const onSubmit = vi.fn()

    render(
      <DepartmentForm
        department={{
          id: '11111111-1111-1111-1111-111111111111',
          code: 'URETIM',
          name: 'Üretim',
          description: 'Eski açıklama',
          active: true,
        }}
        onSubmit={onSubmit}
        onCancel={() => undefined}
      />,
    )

    fireEvent.change(screen.getByLabelText('Departman adı'), {
      target: { value: 'Yeni Üretim' },
    })

    fireEvent.change(screen.getByLabelText('Açıklama'), {
      target: { value: 'Yeni açıklama' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Değişiklikleri kaydet' }))

    expect(onSubmit).toHaveBeenCalledWith({
      code: 'URETIM',
      name: 'Yeni Üretim',
      description: 'Yeni açıklama',
    })
  })

  it('shows server field errors', () => {
    render(
      <DepartmentForm
        serverErrors={{
          code: 'Bu departman kodu zaten kullanılıyor.',
        }}
        onSubmit={() => undefined}
        onCancel={() => undefined}
      />,
    )

    expect(screen.getByText('Bu departman kodu zaten kullanılıyor.')).toBeInTheDocument()
  })
})
