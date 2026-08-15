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
  notify('login')
}

export function updateSessionTokens(session: AuthSession) {
  currentSession = session
  currentStatus = 'authenticated'
  notify('token-refresh')
}

export function clearSession(reason: 'logout' | 'expiry') {
  currentSession = null
  currentStatus = reason === 'expiry' ? 'expired' : 'anonymous'
  notify(reason)
}
