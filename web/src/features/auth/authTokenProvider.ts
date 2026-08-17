import { clearStoredSession, readStoredSession, writeStoredSession } from './sessionStorage'
import type {
  AuthSession,
  AuthTokenProvider,
  SessionChangeListener,
  SessionChangeReason,
  SessionSnapshot,
  SessionStatus,
} from './sessionTypes'


let currentSession: AuthSession | null = null
let currentStatus: SessionStatus = 'anonymous'

const listeners = new Set<SessionChangeListener>()

function getSnapshot(): SessionSnapshot {
  return {
    status: currentStatus,
    session: currentSession,
  }
}

function notify(reason: SessionChangeReason) {
  const snapshot = getSnapshot()

  listeners.forEach((listener) => {
    listener(snapshot, reason)
  })
}

export function initializeAuthSession() {
  const storedSession = readStoredSession()

  currentSession = storedSession
  currentStatus = storedSession ? 'authenticated' : 'anonymous'
}

export const authTokenProvider: AuthTokenProvider = {
  getAccessToken() {
    return currentSession?.accessToken ?? null
  },

  getSession() {
    return currentSession
  },

  getStatus() {
    return currentStatus
  },

  subscribe(listener) {
    listeners.add(listener)

    return () => {
      listeners.delete(listener)
    }
  },
}

export function setAuthenticatedSession(session: AuthSession) {
  currentSession = session
  currentStatus = 'authenticated'

  writeStoredSession(session)
  notify('login')
}

export function updateSessionTokens(session: AuthSession) {
  currentSession = session
  currentStatus = 'authenticated'

  writeStoredSession(session)
  notify('token-refresh')
}

export function updateSessionUser(user: AuthSession['user']) {
  if (!currentSession) {
    return
  }

  currentSession = {
    ...currentSession,
    user,
  }

  writeStoredSession(currentSession)
  notify('profile-update')
}

export function clearSession(reason: 'logout' | 'expiry') {
  currentSession = null
  currentStatus = reason === 'expiry' ? 'expired' : 'anonymous'

  clearStoredSession()
  notify(reason)
}
