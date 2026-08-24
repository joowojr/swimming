export type TaskStatus = 'TODO' | 'DOING' | 'DONE' | 'HOLD'

export interface CreateTaskRequest {
  title: string
}

export interface UpdateTaskRequest {
  title: string
  status: TaskStatus
}

export interface ReorderTasksRequest {
  taskIds: number[]
}

export interface DeleteTasksRequest {
  taskIds: number[]
}

export interface TaskResponse {
  id: number
  projectId: number
  title: string
  status: TaskStatus
  orderIdx: number
  createdAt: string
  updatedAt: string
}

export interface TaskSummaryResponse {
  id: number
  title: string
  status: TaskStatus
  orderIdx: number
}
