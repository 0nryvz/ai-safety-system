export interface LoginCredentials {
  email: string
  password: string
}

export interface AuthUser {
  id: number
  email: string
  role: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  user: AuthUser
}

interface ApiErrorResponse {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
}

export class AuthError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'AuthError'
    this.status = status
  }
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function login(credentials: LoginCredentials): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(credentials),
  })

  if (!response.ok) {
    let errorMessage = 'Giriş işlemi başarısız oldu.'

    try {
      const errorResponse = (await response.json()) as ApiErrorResponse

      if (errorResponse.message) {
        errorMessage = errorResponse.message
      }
    } catch {
      // Backend JSON dışında bir cevap gönderirse varsayılan mesaj kullanılır.
    }

    throw new AuthError(errorMessage, response.status)
  }

  return response.json() as Promise<LoginResponse>
}
