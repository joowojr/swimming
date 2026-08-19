export interface LoginRequest {
  email: string
  password: string
}

export interface AuthUser {
  id: number
  email: string
  nickname: string
  timezone: string
}

export interface AuthResponse {
  accessToken: string
  user: AuthUser
}

export interface RefreshResponse {
  accessToken: string
}

export type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated'

export type LoginFieldErrors = Partial<Record<keyof LoginRequest, string>>
