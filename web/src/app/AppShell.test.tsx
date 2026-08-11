import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import AppShell from './AppShell'

describe('AppShell', () => {
  it('renders the provided page content', () => {
    render(
      <AppShell>
        <h1>Test page content</h1>
      </AppShell>,
    )

    expect(screen.getByRole('heading', { name: 'Test page content' })).toBeInTheDocument()
  })
})
