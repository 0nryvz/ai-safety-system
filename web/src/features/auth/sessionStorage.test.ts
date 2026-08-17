import { afterEach, describe, expect, it } from 'vitest'
import { clearStoredSession, readStoredSession, writeStoredSession } from './sessionStorage'
import type { AuthSession } from './sessionTypes'

const session: AuthSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  user: null,
}

afterEach(() => {
  window.sessionStorage.clear()
})

describe('sessionStorage', () => {
  it('returns null when no session is stored', () => {
    expect(readStoredSession()).toBeNull()
  })

  it('stores and reads an auth session', () => {
    writeStoredSession(session)

    expect(readStoredSession()).toEqual(session)
  })

  it('clears the stored session', () => {
    writeStoredSession(session)
    clearStoredSession()

    expect(readStoredSession()).toBeNull()
  })

  it('removes invalid stored session data', () => {
    window.sessionStorage.setItem('authSession', 'invalid-json')

    expect(readStoredSession()).toBeNull()
    expect(window.sessionStorage.getItem('authSession')).toBeNull()
  })
})
