export type TaskStatus = 'TODO' | 'DOING' | 'DONE' | 'HOLD'
export type TaskListMode = 'all' | 'unclassified'
export type TaskMatrixSection = 'PRIORITY_URGENT' | 'URGENT' | 'PRIORITY' | 'STANDARD'

export interface CreateTaskRequest {
  title: string
  priority?: boolean
  urgent?: boolean
}

export interface UpdateTaskTitleRequest {
  title: string
}

export interface UpdateTaskStatusRequest {
  status: TaskStatus
}

export interface UpdateTaskPriorityRequest {
  priority: boolean
}

export interface UpdateTaskUrgentRequest {
  urgent: boolean
}

export interface DeleteTasksRequest {
  taskIds: number[]
}

export interface TaskResponse {
  id: number
  projectId: number | null
  title: string
  status: TaskStatus
  priority: boolean
  urgent: boolean
  createdAt: string
  updatedAt: string
}

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
