import { publicApiClient } from '../core/api/publicApiClient'

export interface LoginCredentials {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
}

interface TokenRequest {
  refreshToken: string
}

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const response = await publicApiClient.post<AuthResponse>('/auth/login', credentials)

  return response.data
}

export async function refreshAuthTokens(refreshToken: string): Promise<AuthResponse> {
  const request: TokenRequest = {
    refreshToken,
  }

  const response = await publicApiClient.post<AuthResponse>('/auth/refresh', request)

  return response.data
}

export async function logout(refreshToken: string): Promise<void> {
  const request: TokenRequest = {
    refreshToken,
  }

  await publicApiClient.post('/auth/logout', request)
}
