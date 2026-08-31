import { client } from '../../api/client'
import type { AuthResponse, GoogleLoginRequest, RefreshResponse } from './authTypes'

export async function loginWithGoogle(request: GoogleLoginRequest): Promise<AuthResponse> {
  const response = await client.post<AuthResponse>('/auth/google', request)
  return response.data
}

export async function refreshAuthentication(): Promise<RefreshResponse> {
  const response = await client.post<RefreshResponse>('/auth/refresh')
  return response.data
}

export async function logout(): Promise<void> {
  await client.post('/auth/logout')
}
