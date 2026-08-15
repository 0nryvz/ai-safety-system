import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import Button from '../Button/Button'
import ErrorState from './ErrorState'

afterEach(() => {
  cleanup()
})

describe('ErrorState', () => {
  it('renders an accessible error state', () => {
    render(<ErrorState title="Veriler yüklenemedi" />)

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Veriler yüklenemedi' })).toBeInTheDocument()
  })

  it('renders the description when provided', () => {
    render(
      <ErrorState
        title="Bir hata oluştu"
        description="Sunucudan veri alınırken bir hata oluştu."
      />,
    )

    expect(screen.getByText('Sunucudan veri alınırken bir hata oluştu.')).toBeInTheDocument()
  })

  it('renders an optional recovery action', () => {
    render(
      <ErrorState
        title="Veriler yüklenemedi"
        action={<Button type="button">Tekrar dene</Button>}
      />,
    )

    expect(screen.getByRole('button', { name: 'Tekrar dene' })).toBeInTheDocument()
  })
})
