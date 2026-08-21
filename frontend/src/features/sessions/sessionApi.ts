import { client } from '../../api/client'
import type {
  ActiveSessionResponse,
  SessionResponse,
  StartPersonalSessionRequest,
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
