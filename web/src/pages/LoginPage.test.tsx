import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as authActions from '../features/auth/authActions'
import * as authTokenProvider from '../features/auth/authTokenProvider'
import * as authService from '../services/authService'
import LoginPage from './LoginPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('LoginPage', () => {
  it('hydrates the current user before completing login', async () => {
    vi.spyOn(authService, 'login').mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
    })

    const setSessionSpy = vi
      .spyOn(authTokenProvider, 'setAuthenticatedSession')
      .mockImplementation(() => undefined)

    const hydrateSpy = vi.spyOn(authActions, 'hydrateCurrentUser').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      email: 'user@example.com',
      fullName: 'Test User',
      active: true,
      roles: ['OHS_SPECIALIST'],
      departmentIds: [],
    })

    const onLoginSuccess = vi.fn()

    render(<LoginPage onLoginSuccess={onLoginSuccess} />)

    fireEvent.change(screen.getByLabelText('E-posta adresi'), {
      target: {
        value: 'user@example.com',
      },
    })

    fireEvent.change(screen.getByLabelText('Parola'), {
      target: {
        value: 'password',
      },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Giriş yap' }))

    await waitFor(() => {
      expect(setSessionSpy).toHaveBeenCalledWith({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        tokenType: 'Bearer',
        user: null,
      })

      expect(hydrateSpy).toHaveBeenCalledTimes(1)
      expect(onLoginSuccess).toHaveBeenCalledTimes(1)
    })

    expect(setSessionSpy.mock.invocationCallOrder[0]).toBeLessThan(
      hydrateSpy.mock.invocationCallOrder[0],
    )

    expect(hydrateSpy.mock.invocationCallOrder[0]).toBeLessThan(
      onLoginSuccess.mock.invocationCallOrder[0],
    )
  })

  it('does not complete login when user hydration fails', async () => {
    vi.spyOn(authService, 'login').mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
    })

    vi.spyOn(authTokenProvider, 'setAuthenticatedSession').mockImplementation(() => undefined)

    vi.spyOn(authActions, 'hydrateCurrentUser').mockRejectedValue(new Error('Profile load failed'))

    const onLoginSuccess = vi.fn()

    render(<LoginPage onLoginSuccess={onLoginSuccess} />)

    fireEvent.change(screen.getByLabelText('E-posta adresi'), {
      target: {
        value: 'user@example.com',
      },
    })

    fireEvent.change(screen.getByLabelText('Parola'), {
      target: {
        value: 'password',
      },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Giriş yap' }))

    await waitFor(() => {
      expect(onLoginSuccess).not.toHaveBeenCalled()

      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
    expect(authTokenProvider.authTokenProvider.getSession()).toBeNull()
    expect(authTokenProvider.authTokenProvider.getStatus()).toBe('anonymous')
  })
})
