import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Button from './Button'

afterEach(() => {
  cleanup()
})

describe('Button', () => {
  it('renders its content', () => {
    render(<Button>Kaydet</Button>)

    expect(screen.getByRole('button', { name: 'Kaydet' })).toBeInTheDocument()
  })

  it('forwards native button props', () => {
    const handleClick = vi.fn()

    render(
      <Button type="button" onClick={handleClick}>
        Devam et
      </Button>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Devam et' }))

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('does not trigger click when disabled', () => {
    const handleClick = vi.fn()

    render(
      <Button disabled onClick={handleClick}>
        Kaydet
      </Button>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Kaydet' }))

    expect(handleClick).not.toHaveBeenCalled()
  })

  it('applies the selected variant', () => {
    render(<Button variant="danger">Sil</Button>)

    expect(screen.getByRole('button', { name: 'Sil' })).toHaveClass('ui-button--danger')
  })
})
