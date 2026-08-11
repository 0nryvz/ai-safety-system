import { env } from './env'

export const featureFlags = {
  mockData: env.enableMockData,
  debugLogging: env.enableDebugLogging,
} as const

export type FeatureFlag = keyof typeof featureFlags

export function isFeatureEnabled(flag: FeatureFlag) {
  return featureFlags[flag]
}
