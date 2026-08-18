import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { clearSession, setAuthenticatedSession } from './features/auth/authTokenProvider'
import type { AuthSession } from './features/auth/sessionTypes'

const session: AuthSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  user: {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'user@example.com',
    fullName: 'Test User',
    active: true,
    roles: ['OHS_SPECIALIST'],
    departmentIds: [],
  },
}

beforeEach(() => {
  window.sessionStorage.clear()
  clearSession('logout')
})

afterEach(() => {
  cleanup()
  window.sessionStorage.clear()
  clearSession('logout')
})

function renderApp(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <App />
    </MemoryRouter>,
  )
}

describe('App auth routing', () => {
  it('redirects an anonymous user from the dashboard to login', () => {
    renderApp('/dashboard')

    expect(
      screen.getByRole('heading', {
        name: 'AI Safety System',
      }),
    ).toBeInTheDocument()

    expect(screen.getByText('Yönetim paneline giriş yapın')).toBeInTheDocument()
  })

  it('allows an authenticated user to open the dashboard', () => {
    setAuthenticatedSession(session)

    renderApp('/dashboard')

    expect(
      screen.getByRole('heading', {
        name: 'Operasyon Dashboardu',
      }),
    ).toBeInTheDocument()
  })

  it('redirects an authenticated user away from login', () => {
    setAuthenticatedSession(session)

    renderApp('/login')

    expect(
      screen.getByRole('heading', {
        name: 'Operasyon Dashboardu',
      }),
    ).toBeInTheDocument()
  })

  it('routes the home page to login for anonymous users', () => {
    renderApp('/')

    expect(screen.getByText('Yönetim paneline giriş yapın')).toBeInTheDocument()
  })

  it('routes the home page to dashboard for authenticated users', () => {
    setAuthenticatedSession(session)

    renderApp('/')

    expect(
      screen.getByRole('heading', {
        name: 'Operasyon Dashboardu',
      }),
    ).toBeInTheDocument()
  })

  it('blocks protected content after session expiry', () => {
    setAuthenticatedSession(session)
    clearSession('expiry')

    renderApp('/dashboard')

    expect(screen.getByText('Yönetim paneline giriş yapın')).toBeInTheDocument()
  })
  it('keeps an authenticated session without a hydrated user on login', () => {
    setAuthenticatedSession({
      ...session,
      user: null,
    })

    renderApp('/login')

    expect(screen.getByText('Yönetim paneline giriş yapın')).toBeInTheDocument()
  })
})
