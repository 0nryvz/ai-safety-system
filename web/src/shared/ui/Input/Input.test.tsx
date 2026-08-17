import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Input from './Input'

afterEach(() => {
  cleanup()
})

describe('Input', () => {
  it('associates the label with the input', () => {
    render(<Input id="email" label="E-posta" />)

    expect(screen.getByLabelText('E-posta')).toHaveAttribute('id', 'email')
  })

  it('forwards native input props', () => {
    const handleChange = vi.fn()

    render(
      <Input
        id="name"
        label="Ad"
        type="text"
        placeholder="Adınızı girin"
        onChange={handleChange}
      />,
    )

    const input = screen.getByLabelText('Ad')

    expect(input).toHaveAttribute('placeholder', 'Adınızı girin')

    fireEvent.change(input, {
      target: {
        value: 'Ahmet',
      },
    })

    expect(handleChange).toHaveBeenCalledTimes(1)
  })

  it('shows an accessible error message', () => {
    render(<Input id="email" label="E-posta" error="Geçerli bir e-posta girin." />)

    const input = screen.getByLabelText('E-posta')
    const error = screen.getByRole('alert')

    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', 'email-error')
    expect(error).toHaveAttribute('id', 'email-error')
    expect(error).toHaveTextContent('Geçerli bir e-posta girin.')
  })

  it('supports the disabled state', () => {
    render(<Input id="email" label="E-posta" disabled />)

    expect(screen.getByLabelText('E-posta')).toBeDisabled()
  })
})
