/** 백엔드 agentwork DTO와 같은 모양. 계약이 바뀌면 여기와 백엔드를 함께 고친다. */

export type AgentType = 'CLAUDE_CODE' | 'CODEX'

export type AgentWorkStatus = 'WORKING' | 'WAITING' | 'COMPLETED' | 'FAILED' | 'UNKNOWN'

export type StatusSource = 'MCP_REPORT'

export type WorkResourceType = 'SWIMMING_TASK'

export type BoardLane = 'NOT_STARTED' | 'WORKING' | 'WAITING' | 'COMPLETED' | 'ATTENTION'
export type AgentBoardSort = 'PRIORITY' | 'ASC' | 'DESC'
export type TaskBoardStatus = 0 | 1 | 2

export type AgentSessionEventType =
  | 'STARTED'
  | 'PROGRESS_REPORTED'
  | 'WAITING_FOR_USER'
  | 'COMPLETED'
  | 'FAILED'
  | 'SIGNAL_LOST'

/** 출처와 무관한 Work Item. Swimming 할 일은 폴더를 container로 담고, 미분류면 null이다. */
export interface WorkItem {
  type: WorkResourceType
  id: string
  title: string
  containerId: number | null
  containerName: string | null
  status: TaskBoardStatus
  important: boolean
  urgent: boolean
}

export interface AgentSession {
  id: number
  workItemIds: number[]
  agentType: AgentType
  status: AgentWorkStatus
  statusSource: StatusSource
  instruction: string | null
  summary: string | null
  startedAt: string
  lastSeenAt: string
  completedAt: string | null
}

/** 보드의 카드 하나(= 할 일 하나). 여러 할 일이 하나의 Agent 세션을 공유할 수 있고, 아직 시작 전이면 session이 null이다. */
export interface AgentWorkItem {
  /** agent_work_items 식별자. 아직 Agent Work에 등록되지 않은 Task는 null이다. */
  id: number | null
  lane: BoardLane
  workItem: WorkItem
  session: AgentSession | null
  lastActivityAt: string
}

export interface AgentBoard {
  notStarted: AgentWorkItem[]
  working: AgentWorkItem[]
  waiting: AgentWorkItem[]
  completed: AgentWorkItem[]
  attention: AgentWorkItem[]
}

/** 끝난 세션을 다른 Agent가 다시 열 수 있어 agentType은 이벤트가 기록될 때의 값이다. */
export interface AgentSessionEvent {
  id: number
  sessionId: number
  agentType: AgentType
  eventType: AgentSessionEventType
  summary: string | null
  source: StatusSource
  createdAt: string
}

export interface AddWorkItemRequest {
  resourceType: WorkResourceType
  resourceId: string
}

export interface AgentAccessToken {
  id: number
  name: string
  tokenPrefix: string
  tokenSuffix: string
  scopes: string[]
  expiresAt: string | null
  lastUsedAt: string | null
  revokedAt: string | null
  createdAt: string
}

export interface IssuedAgentAccessToken {
  id: number
  name: string
  token: string
  scopes: string[]
  expiresAt: string | null
}

/** 보드에서 새로 만드는 할 일 초안. 실제 생성 API가 준비되기 전까지 입력 기능은 비활성화한다. */
export interface NewWorkItemDraft {
  title: string
  containerId: number | null
  containerName: string | null
  important: boolean
  urgent: boolean
  planDate?: string | null
}
