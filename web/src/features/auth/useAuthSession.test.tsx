import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  clearSession,
  setAuthenticatedSession,
} from './authTokenProvider'
import type { AuthSession } from './sessionTypes'
import { useAuthSession } from './useAuthSession'

const session: AuthSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  user: null,
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

describe('useAuthSession', () => {
  it('returns the current anonymous state', () => {
    const { result } = renderHook(() => useAuthSession())

    expect(result.current.status).toBe('anonymous')
    expect(result.current.session).toBeNull()
  })

  it('updates when a user becomes authenticated', () => {
    const { result } = renderHook(() => useAuthSession())

    act(() => {
      setAuthenticatedSession(session)
    })

    expect(result.current.status).toBe('authenticated')
    expect(result.current.session).toEqual(session)
  })

  it('updates when the session is cleared', () => {
    setAuthenticatedSession(session)

    const { result } = renderHook(() => useAuthSession())

    act(() => {
      clearSession('logout')
    })

    expect(result.current.status).toBe('anonymous')
    expect(result.current.session).toBeNull()
  })

  it('exposes the expired state', () => {
    setAuthenticatedSession(session)

    const { result } = renderHook(() => useAuthSession())

    act(() => {
      clearSession('expiry')
    })

    expect(result.current.status).toBe('expired')
    expect(result.current.session).toBeNull()
  })
})