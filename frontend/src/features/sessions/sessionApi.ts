import { client } from '../../api/client'
import type {
  ActiveSessionResponse,
  SessionDetailResponse,
  SessionResponse,
  StartPersonalSessionRequest,
  UpdateSessionMusicUrlRequest,
} from './sessionTypes'

export async function startPersonalSession(
  request: StartPersonalSessionRequest,
): Promise<SessionResponse> {
  const response = await client.post<SessionResponse>('/sessions', request)
  return response.data
}

export async function endSession(sessionId: number): Promise<SessionResponse> {
  const response = await client.post<SessionResponse>(`/sessions/${sessionId}/end`)
  return response.data
}

export async function getActiveSession(): Promise<ActiveSessionResponse | null> {
  const response = await client.get<ActiveSessionResponse>('/sessions/active')
  return response.status === 204 ? null : response.data
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
