import { client } from '../../api/client'
import type {
  EndSessionRequest,
  SessionDetailResponse,
  SessionResponse,
  StartPersonalSessionRequest,
  UpdateSessionMusicUrlRequest,
  UpdateSessionFocusDurationRequest,
} from './sessionTypes'

export async function startPersonalSession(
  request: StartPersonalSessionRequest,
): Promise<SessionDetailResponse> {
  const response = await client.post<SessionDetailResponse>('/sessions', request)
  return response.data
}

export async function endSession(
  sessionId: number,
  request?: EndSessionRequest,
): Promise<SessionResponse> {
  const response = await client.post<SessionResponse>(`/sessions/${sessionId}/end`, request)
  return response.data
}

export async function getActiveSession(): Promise<SessionDetailResponse | null> {
  const response = await client.get<SessionDetailResponse>('/sessions/active')
  return response.status === 204 ? null : response.data
}

export async function getSessions(): Promise<SessionDetailResponse[]> {
  const response = await client.get<SessionDetailResponse[]>('/sessions')
  return response.data
}

export async function getSession(sessionId: number): Promise<SessionDetailResponse> {
  const response = await client.get<SessionDetailResponse>(`/sessions/${sessionId}`)
  return response.data
}

export async function updateSessionMusicUrl(
  sessionId: number,
  request: UpdateSessionMusicUrlRequest,
): Promise<void> {
  await client.put(`/sessions/${sessionId}/music-url`, request)
}

export async function updateSessionFocusDuration(
  sessionId: number,
  request: UpdateSessionFocusDurationRequest,
): Promise<void> {
  await client.put(`/sessions/${sessionId}/focus-duration`, request)
}
