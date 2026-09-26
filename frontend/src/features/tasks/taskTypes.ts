export type TaskStatus = 'TODO' | 'DOING' | 'DONE' | 'HOLD'
/** 할 일 목록 정렬. 기준은 생성 시각이고 목록을 좁히지 않으므로 필터와 분리한다. */
export type TaskSort = 'desc' | 'asc'
export type TaskMatrixSection = 'PRIORITY_URGENT' | 'URGENT' | 'PRIORITY' | 'STANDARD'

export interface UpdateTaskTitleRequest {
  title: string
}

export interface UpdateTaskStatusRequest {
  status: TaskStatus
}

/** @deprecated UpdateTaskInfoRequest의 priority를 쓴다. */
export interface UpdateTaskPriorityRequest {
  priority: boolean
}

/** @deprecated UpdateTaskInfoRequest의 urgent를 쓴다. */
export interface UpdateTaskUrgentRequest {
  urgent: boolean
}

/**
 * 할 일의 정보를 한 번에 바꾼다. 사용자에게는 모달 하나의 저장이라 서버가 한 트랜잭션으로 처리한다.
 * folderId는 빼면 폴더를 바꾸지 않고, null이면 미분류로 옮긴다.
 * planDate는 null이 "캘린더에 없음"을 뜻해 생략과 구분되지 않으므로 항상 보낸다.
 */
export interface UpdateTaskInfoRequest {
  title: string
  folderId?: number | null
  priority: boolean
  urgent: boolean
  planDate: string | null
}

export interface DeleteTasksRequest {
  taskIds: number[]
}

/** 한 번에 만들 할 일 하나. 필드 구성은 POST /api/tasks의 본문과 같다. */
export interface CreateTaskDraft {
  title: string
  folderId?: number | null
  priority?: boolean
  urgent?: boolean
  planDate?: string | null
}

/**
 * 모달 하나에서 담은 할 일을 한 번에 만든다.
 * 사용자에게는 "모두 추가" 한 번이라 서버가 한 트랜잭션으로 처리하고,
 * 하나라도 실패하면 아무것도 만들지 않는다. 부분 성공 상태는 없다.
 */
export interface CreateTasksBatchRequest {
  tasks: CreateTaskDraft[]
}

export interface TaskResponse {
  id: number
  folderId: number | null
  title: string
  status: TaskStatus
  priority: boolean
  urgent: boolean
  /** 캘린더에 담긴 날짜. 한 할 일은 날짜 하나에만 담기고, 담지 않았으면 null이다. */
  planDate: string | null
  createdAt: string
  updatedAt: string
}

/** store가 캐시하는 task의 최소 모양. 캘린더 응답처럼 createdAt·updatedAt이 없는 곳에서도 채울 수 있다. */
export type TaskCacheEntry = Omit<TaskResponse, 'createdAt' | 'updatedAt'>

export interface TaskMatrixItem extends TaskResponse {
  positionCursor: string
}

export interface TaskMatrixPageResponse {
  section: TaskMatrixSection
  items: TaskMatrixItem[]
  nextCursor: string | null
  hasNext: boolean
}

export interface TaskPlacementRequest {
  scope: 'MATRIX'
  targetSection: TaskMatrixSection
  previousTaskId: number | null
  nextTaskId: number | null
  status?: TaskStatus
}

export interface TaskPlacementResponse {
  scope: 'MATRIX'
  task: TaskMatrixItem
  sourceSection: TaskMatrixSection
  targetSection: TaskMatrixSection
  rebalancedSections: TaskMatrixSection[]
}

export interface TaskSummaryResponse {
  id: number
  title: string
  status: TaskStatus
  priority: boolean
  urgent: boolean
  planDate: string | null
}
