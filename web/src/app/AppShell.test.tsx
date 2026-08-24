import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as authActions from '../features/auth/authActions'
import AppShell from './AppShell'
import { MemoryRouter } from 'react-router-dom'
import { clearSession, setAuthenticatedSession } from '../features/auth/authTokenProvider'
import type { AuthSession } from '../features/auth/sessionTypes'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  window.sessionStorage.clear()
  clearSession('logout')
})

const adminSession: AuthSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  user: {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'admin@example.com',
    fullName: 'Admin User',
    active: true,
    roles: ['ADMIN'],
    departmentIds: [],
  },
}

const specialistSession: AuthSession = {
  ...adminSession,
  user: {
    ...adminSession.user!,
    roles: ['OHS_SPECIALIST'],
  },
}

describe('AppShell', () => {
  it('renders SafeSight header branding', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <div>Content</div>
        </AppShell>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'SafeSight' })).toBeInTheDocument()
    expect(screen.queryByText('AI Safety System')).not.toBeInTheDocument()
    expect(screen.getByText('Gerçek Zamanlı Güvenlik İzleme Paneli')).toBeInTheDocument()
    expect(document.querySelector('.app-header__logo')).toBeInTheDocument()
  })

  it('renders the provided page content', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <h1>Test page content</h1>
        </AppShell>
        ,
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Test page content' })).toBeInTheDocument()
  })

  it('renders the logout action', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <div>Content</div>
        </AppShell>
        ,
      </MemoryRouter>,
    )

    expect(screen.getByRole('button', { name: 'Çıkış yap' })).toBeInTheDocument()
  })

  it('performs logout when the logout button is clicked', async () => {
    const logoutSpy = vi.spyOn(authActions, 'performLogout').mockResolvedValue()

    render(
      <MemoryRouter>
        <AppShell>
          <div>Content</div>
        </AppShell>
        ,
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Çıkış yap' }))

    await waitFor(() => {
      expect(logoutSpy).toHaveBeenCalledTimes(1)
    })
  })

  it('shows admin management links to admins', () => {
    setAuthenticatedSession(adminSession)

    render(
      <MemoryRouter>
        <AppShell>
          <div>Content</div>
        </AppShell>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Kameralar' })).toHaveAttribute(
      'href',
      '/admin/cameras',
    )

    expect(screen.getByRole('link', { name: 'Kullanıcılar' })).toHaveAttribute(
      'href',
      '/admin/users',
    )
  })

  it('hides admin management links from non-admin users', () => {
    setAuthenticatedSession(specialistSession)

    render(
      <MemoryRouter>
        <AppShell>
          <div>Content</div>
        </AppShell>
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link', { name: 'Kameralar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Kullanıcılar' })).not.toBeInTheDocument()
  })
})
