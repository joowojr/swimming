// 이동 응답에만 필요한 타입 참조다. C안(캘린더 날짜를 task 컬럼으로)으로 가면 이 import가 사라진다.
// TODO(task-owns-plan-date): docs/backlog/task-owns-plan-date.md
import type { DailyPlan } from '../calendar/dailyPlanTypes'

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
 * 할 일의 분류 정보를 한 번에 바꾼다. 폴더·중요·즉시는 task의 속성이고 캘린더 날짜는 별도 테이블이지만,
 * 사용자에게는 모달 하나의 저장이라 서버가 한 트랜잭션으로 처리한다.
 * folderId는 null이 "미분류"를 뜻해 생략과 구분되지 않으므로 세 값을 항상 보낸다.
 * 캘린더 항목이 없는 화면(폴더 목록·매트릭스)에서는 plan을 보내지 않는다.
 * TODO(task-owns-plan-date): 캘린더 날짜가 task 컬럼이 되면 plan 객체는 planDate 한 필드로 줄어든다.
 *   docs/backlog/task-owns-plan-date.md
 */
export interface UpdateTaskInfoRequest {
  title: string
  folderId: number | null
  priority: boolean
  urgent: boolean
  plan?: {
    /** 캘린더 항목 id. 이미 담긴 항목을 옮길 때만 보낸다. 없으면 그 날짜의 캘린더에 새로 담는다. */
    itemId?: number
    date: string
  }
}

export interface UpdateTaskInfoResponse {
  task: TaskResponse
  /** 이동으로 바뀐 날짜들의 캘린더. 캘린더을 옮기지 않았으면 빈 배열이다. */
  plans: DailyPlan[]
}

export interface DeleteTasksRequest {
  taskIds: number[]
}

export interface TaskResponse {
  id: number
  folderId: number | null
  title: string
  status: TaskStatus
  priority: boolean
  urgent: boolean
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
}
