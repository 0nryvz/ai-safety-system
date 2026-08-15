export type UserRole = 'ADMIN' | 'OHS_SPECIALIST' | 'SHIFT_SUPERVISOR'

export interface AuthUser {
  id: string
  email: string
  fullName: string
  active: boolean
  roles: UserRole[]
  departmentIds: string[]
}

export interface AuthSession {
  accessToken: string
  refreshToken: string
  tokenType: string
  user: AuthUser | null
}

export type SessionStatus = 'anonymous' | 'authenticated' | 'expired'

export interface AuthTokenProvider {
  getAccessToken(): string | null
  getSession(): AuthSession | null
  getStatus(): SessionStatus
  subscribe(listener: SessionChangeListener): () => void
}

export type SessionChangeReason = 'login' | 'token-refresh' | 'logout' | 'expiry'

export interface SessionSnapshot {
  status: SessionStatus
  session: AuthSession | null
}

export type SessionChangeListener = (snapshot: SessionSnapshot, reason: SessionChangeReason) => void
