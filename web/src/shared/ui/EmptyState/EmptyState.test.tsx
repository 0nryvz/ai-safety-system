import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import Button from '../Button/Button'
import EmptyState from './EmptyState'

afterEach(() => {
  cleanup()
})

describe('EmptyState', () => {
  it('renders the title', () => {
    render(<EmptyState title="Kayıt bulunamadı" />)

    expect(screen.getByRole('heading', { name: 'Kayıt bulunamadı' })).toBeInTheDocument()
  })

  it('renders the description when provided', () => {
    render(
      <EmptyState
        title="İhlal bulunamadı"
        description="Seçili filtrelere uygun bir ihlal kaydı bulunamadı."
      />,
    )

    expect(
      screen.getByText('Seçili filtrelere uygun bir ihlal kaydı bulunamadı.'),
    ).toBeInTheDocument()
  })

  it('does not render a description when it is not provided', () => {
    render(<EmptyState title="Kayıt bulunamadı" />)

    expect(screen.queryByRole('paragraph')).not.toBeInTheDocument()
  })

  it('renders an optional action', () => {
    render(<EmptyState title="Kamera bulunamadı" action={<Button type="button">Yenile</Button>} />)

    expect(screen.getByRole('button', { name: 'Yenile' })).toBeInTheDocument()
  })
})
