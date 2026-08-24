import { client } from '../../api/client'
import type {
  EndSessionRequest,
  SessionDetailResponse,
  SessionResponse,
  StartPersonalSessionRequest,
  UpdateSessionMusicUrlRequest,
  UpdateSessionPlannedDurationRequest,
} from './sessionTypes'

export async function startPersonalSession(
  request: StartPersonalSessionRequest,
): Promise<SessionResponse> {
  const response = await client.post<SessionResponse>('/sessions', request)
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

export async function updateSessionPlannedDuration(
  sessionId: number,
  request: UpdateSessionPlannedDurationRequest,
): Promise<void> {
  await client.put(`/sessions/${sessionId}/planned-duration`, request)
}
