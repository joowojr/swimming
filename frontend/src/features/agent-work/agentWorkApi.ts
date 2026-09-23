import { EventSource } from 'eventsource'
import { ACCESS_TOKEN_KEY, API_BASE_URL, client } from '../../api/client'
import { createTaskWithOptionalPlan } from '../tasks/taskApi'
import type {
  AddWorkItemRequest,
  AgentBoard,
  AgentBoardSort,
  AgentSessionEvent,
  AgentWorkItem,
  AgentAccessToken,
  IssuedAgentAccessToken,
  NewWorkItemDraft,
} from './agentWorkTypes'

export interface CreateAgentAccessTokenRequest {
  name: string
  expiresInDays?: number
}

export async function getAgentAccessTokens(): Promise<AgentAccessToken[]> {
  const response = await client.get<AgentAccessToken[]>('/agent-work/tokens')
  return response.data
}

export async function createAgentAccessToken(
  request: CreateAgentAccessTokenRequest,
): Promise<IssuedAgentAccessToken> {
  const response = await client.post<IssuedAgentAccessToken>('/agent-work/tokens', request)
  return response.data
}

export async function revokeAgentAccessToken(tokenId: number): Promise<void> {
  await client.delete(`/agent-work/tokens/${tokenId}`)
}

export async function getBoard(sort: AgentBoardSort = 'PRIORITY'): Promise<AgentBoard> {
  const response = await client.get<AgentBoard>('/agent-work/board', { params: { sort } })
  return response.data
}

export async function getEvents(workItemId: number): Promise<AgentSessionEvent[]> {
  const response = await client.get<AgentSessionEvent[]>(`/agent-work/work-items/${workItemId}/events`)
  return response.data
}

export async function getSessionEvents(sessionId: number): Promise<AgentSessionEvent[]> {
  const response = await client.get<AgentSessionEvent[]>(`/agent-work/sessions/${sessionId}/events`)
  return response.data
}

/**
 * 보드 변경 알림 스트림. 표준 EventSource와 같게 동작하며 fetch만 바꿔 Authorization 헤더를 붙인다.
 * 연결될 때마다 토큰을 새로 읽으므로 갱신된 토큰으로 다시 연결된다.
 */
export function openAgentWorkEvents(): EventSource {
  return new EventSource(`${API_BASE_URL}/agent-work/events`, {
    fetch: (input, init) => {
      const token = localStorage.getItem(ACCESS_TOKEN_KEY)
      return fetch(input, {
        ...init,
        credentials: 'include',
        headers: token ? { ...init.headers, Authorization: `Bearer ${token}` } : init.headers,
      })
    },
  })
}

/** 기존 할 일을 보드로 가져온다. 이미 보드에 있으면 그 카드를 돌려준다. */
export async function addWorkItem(request: AddWorkItemRequest): Promise<AgentWorkItem> {
  const response = await client.post<AgentWorkItem>('/agent-work/work-items', request)
  return response.data
}

/** 새 할 일 생성은 아직 API 계약이 없다. */
export async function addNewWorkItem(draft: NewWorkItemDraft): Promise<AgentWorkItem> {
  const task = await createTaskWithOptionalPlan({
    title: draft.title,
    folderId: draft.containerId,
    priority: draft.important,
    urgent: draft.urgent,
    planDate: draft.planDate,
  })
  return addWorkItem({ resourceType: 'SWIMMING_TASK', resourceId: String(task.id) })
}
