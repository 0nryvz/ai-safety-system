function readBoolean(value: string | undefined, defaultValue = false) {
  if (value === undefined) {
    return defaultValue
  }

  return value.toLowerCase() === 'true'
}

export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  websocketUrl: import.meta.env.VITE_WEBSOCKET_URL ?? '',
  enableMockData: readBoolean(import.meta.env.VITE_ENABLE_MOCK_DATA),
  enableDebugLogging: readBoolean(import.meta.env.VITE_ENABLE_DEBUG_LOGGING),
} as const
