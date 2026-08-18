const REFRESH_LEAD_TIME_MS = 30_000
const MAX_TIMEOUT_MS = 2_147_483_647

export function getClipUrlRefreshDelay(expiresAt: string, now: number = Date.now()): number | null {
  const expirationTime = Date.parse(expiresAt)

  if (Number.isNaN(expirationTime)) {
    return null
  }

  const remainingTime = expirationTime - now

  if (remainingTime <= 0) {
    return null
  }

  const leadTime = Math.min(REFRESH_LEAD_TIME_MS, remainingTime / 2)

  return Math.min(remainingTime - leadTime, MAX_TIMEOUT_MS)
}
