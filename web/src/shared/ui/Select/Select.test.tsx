import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Select from './Select'

afterEach(() => {
  cleanup()
})

describe('Select', () => {
  it('associates the label with the select', () => {
    render(
      <Select id="role" label="Rol">
        <option value="ADMIN">Admin</option>
      </Select>,
    )

    expect(screen.getByLabelText('Rol')).toHaveAttribute('id', 'role')
  })

  it('forwards native select props and change events', () => {
    const handleChange = vi.fn()

    render(
      <Select id="role" label="Rol" defaultValue="" onChange={handleChange}>
        <option value="">Rol seçin</option>
        <option value="ADMIN">Admin</option>
        <option value="OHS_SPECIALIST">İSG Uzmanı</option>
      </Select>,
    )

    const select = screen.getByLabelText('Rol')

    fireEvent.change(select, {
      target: {
        value: 'ADMIN',
      },
    })

    expect(select).toHaveValue('ADMIN')
    expect(handleChange).toHaveBeenCalledTimes(1)
  })

  it('shows an accessible error message', () => {
    render(
      <Select id="role" label="Rol" error="Bir rol seçin.">
        <option value="">Rol seçin</option>
      </Select>,
    )

    const select = screen.getByLabelText('Rol')
    const error = screen.getByRole('alert')

    expect(select).toHaveAttribute('aria-invalid', 'true')
    expect(select).toHaveAttribute('aria-describedby', 'role-error')
    expect(error).toHaveAttribute('id', 'role-error')
    expect(error).toHaveTextContent('Bir rol seçin.')
  })

  it('supports the disabled state', () => {
    render(
      <Select id="role" label="Rol" disabled>
        <option value="ADMIN">Admin</option>
      </Select>,
    )

    expect(screen.getByLabelText('Rol')).toBeDisabled()
  })
})
