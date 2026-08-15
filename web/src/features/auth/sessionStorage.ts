import type { AuthSession } from './sessionTypes'

const SESSION_STORAGE_KEY = 'authSession'

export function readStoredSession(): AuthSession | null {
  const rawSession = window.sessionStorage.getItem(SESSION_STORAGE_KEY)

  if (!rawSession) {
    return null
  }

  try {
    return JSON.parse(rawSession) as AuthSession
  } catch {
    window.sessionStorage.removeItem(SESSION_STORAGE_KEY)
    return null
  }
}

export function writeStoredSession(session: AuthSession) {
  window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredSession() {
  window.sessionStorage.removeItem(SESSION_STORAGE_KEY)
}
