import { hydrateCurrentUser } from './authActions'
import { authTokenProvider, clearSession, initializeAuthSession } from './authTokenProvider'

export async function bootstrapAuthSession(): Promise<void> {
  initializeAuthSession()

  const session = authTokenProvider.getSession()

  if (!session) {
    return
  }

  try {
    await hydrateCurrentUser()
  } catch {
    clearSession('expiry')
  }
}
