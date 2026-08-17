import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as authActions from '../features/auth/authActions'
import AppShell from './AppShell'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('AppShell', () => {
  it('renders the provided page content', () => {
    render(
      <AppShell>
        <h1>Test page content</h1>
      </AppShell>,
    )

    expect(screen.getByRole('heading', { name: 'Test page content' })).toBeInTheDocument()
  })

  it('renders the logout action', () => {
    render(
      <AppShell>
        <div>Content</div>
      </AppShell>,
    )

    expect(screen.getByRole('button', { name: 'Çıkış yap' })).toBeInTheDocument()
  })

  it('performs logout when the logout button is clicked', async () => {
    const logoutSpy = vi.spyOn(authActions, 'performLogout').mockResolvedValue()

    render(
      <AppShell>
        <div>Content</div>
      </AppShell>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Çıkış yap' }))

    await waitFor(() => {
      expect(logoutSpy).toHaveBeenCalledTimes(1)
    })
  })
})
