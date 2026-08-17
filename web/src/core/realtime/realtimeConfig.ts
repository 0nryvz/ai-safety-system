import { env } from '../../config/env'

export const REALTIME_ENDPOINT = '/ws'
export const ALERTS_DESTINATION = '/user/queue/alerts'

interface BrowserLocation {
  protocol: string
  host: string
}

export function resolveWebSocketUrl(
  configuredUrl = env.websocketUrl,
  browserLocation: BrowserLocation = window.location,
) {
  const value = configuredUrl.trim()

  if (/^wss?:\/\//i.test(value)) {
    return value
  }

  if (/^https?:\/\//i.test(value)) {
    const url = new URL(value)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'

    return url.toString()
  }

  const path = value || REALTIME_ENDPOINT
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const protocol = browserLocation.protocol === 'https:' ? 'wss:' : 'ws:'

  return `${protocol}//${browserLocation.host}${normalizedPath}`
}
