import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import StatusBadge from './StatusBadge'

afterEach(() => {
  cleanup()
})

describe('StatusBadge', () => {
  it('renders its content', () => {
    render(<StatusBadge>ONLINE</StatusBadge>)

    expect(screen.getByText('ONLINE')).toBeInTheDocument()
  })

  it('uses the neutral variant by default', () => {
    render(<StatusBadge>UNKNOWN</StatusBadge>)

    expect(screen.getByText('UNKNOWN')).toHaveClass('ui-status-badge--neutral')
  })

  it('applies the selected variant', () => {
    render(<StatusBadge variant="success">READY</StatusBadge>)

    expect(screen.getByText('READY')).toHaveClass('ui-status-badge--success')
  })

  it('supports critical statuses', () => {
    render(<StatusBadge variant="critical">ERROR</StatusBadge>)

    expect(screen.getByText('ERROR')).toHaveClass('ui-status-badge--critical')
  })
})
