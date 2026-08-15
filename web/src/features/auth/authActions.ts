import { logout } from '../../services/authService'
import { getCurrentUser, mapUserResponseToAuthUser } from '../../services/userService'
import { authTokenProvider, clearSession, updateSessionUser } from './authTokenProvider'
import type { AuthUser } from './sessionTypes'

export async function hydrateCurrentUser(): Promise<AuthUser> {
  const session = authTokenProvider.getSession()

  if (!session) {
    throw new Error('Authenticated session is not available.')
  }

  const response = await getCurrentUser()
  const user = mapUserResponseToAuthUser(response)

  updateSessionUser(user)

  return user
}

export async function performLogout(): Promise<void> {
  const session = authTokenProvider.getSession()

  if (!session?.refreshToken) {
    clearSession('logout')
    return
  }

  try {
    await logout(session.refreshToken)
  } finally {
    clearSession('logout')
  }
}
