import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { clearSession, setAuthenticatedSession } from '../features/auth/authTokenProvider'
import type { AuthSession } from '../features/auth/sessionTypes'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import RequireRole from './RequireRole'

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

beforeEach(() => {
  window.sessionStorage.clear()
  clearSession('logout')
})

afterEach(() => {
  cleanup()
  window.sessionStorage.clear()
  clearSession('logout')
})

function renderAdminRoute() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/dashboard" element={<div>Dashboard Page</div>} />

        <Route element={<RequireRole access="admin" />}>
          <Route path="/admin" element={<div>Admin Page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireRole', () => {
  it('redirects anonymous users to login', () => {
    renderAdminRoute()

    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('allows users with the required role', () => {
    setAuthenticatedSession(adminSession)

    renderAdminRoute()

    expect(screen.getByText('Admin Page')).toBeInTheDocument()
  })

  it('redirects authenticated users without the required role', () => {
    setAuthenticatedSession(specialistSession)

    renderAdminRoute()

    expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
  })

  it('denies access when authenticated session has no user summary', () => {
    setAuthenticatedSession({
      ...adminSession,
      user: null,
    })

    renderAdminRoute()

    expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
  })
})
