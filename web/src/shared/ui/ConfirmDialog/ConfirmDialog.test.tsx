import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConfirmDialog from './ConfirmDialog'

afterEach(() => {
  cleanup()
})

describe('ConfirmDialog', () => {
  it('does not render when closed', () => {
    render(
      <ConfirmDialog open={false} title="İşlemi onayla" onConfirm={vi.fn()} onCancel={vi.fn()} />,
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('renders an accessible dialog when open', () => {
    render(
      <ConfirmDialog
        open
        title="Kullanıcıyı sil"
        description="Bu işlem geri alınamaz."
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    const dialog = screen.getByRole('dialog', {
      name: 'Kullanıcıyı sil',
    })

    expect(dialog).toBeInTheDocument()
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByText('Bu işlem geri alınamaz.')).toBeInTheDocument()
  })

  it('calls onConfirm when the confirm button is clicked', () => {
    const handleConfirm = vi.fn()

    render(
      <ConfirmDialog
        open
        title="İşlemi onayla"
        confirmLabel="Sil"
        onConfirm={handleConfirm}
        onCancel={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Sil' }))

    expect(handleConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when the cancel button is clicked', () => {
    const handleCancel = vi.fn()

    render(<ConfirmDialog open title="İşlemi onayla" onConfirm={vi.fn()} onCancel={handleCancel} />)

    fireEvent.click(screen.getByRole('button', { name: 'İptal' }))

    expect(handleCancel).toHaveBeenCalledTimes(1)
  })

  it('supports a danger confirmation action', () => {
    render(
      <ConfirmDialog
        open
        title="Kamerayı sil"
        confirmLabel="Sil"
        confirmVariant="danger"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: 'Sil' })).toHaveClass('ui-button--danger')
  })
})
