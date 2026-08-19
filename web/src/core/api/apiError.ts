export interface ApiErrorResponse {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
  fieldErrors?: Record<string, string>
}

export class ApiError extends Error {
  status: number
  response?: ApiErrorResponse

  constructor(message: string, status: number, response?: ApiErrorResponse) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.response = response
  }
}
