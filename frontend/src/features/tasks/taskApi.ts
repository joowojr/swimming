import { client } from '../../api/client'
import type {
  CreateTaskRequest,
  DeleteTasksRequest,
  TaskListMode,
  TaskResponse,
  UpdateTaskStatusRequest,
  UpdateTaskTitleRequest,
  UpdateTaskPriorityRequest,
  UpdateTaskUrgentRequest,
  TaskMatrixPageResponse,
  TaskMatrixSection,
} from './taskTypes'

/** @deprecated 새 Task 생성에는 createTaskWithOptionalPlan을 사용합니다. */
export async function createTask(
  projectId: number,
  request: CreateTaskRequest,
): Promise<TaskResponse> {
  const response = await client.post<TaskResponse>(
    `/projects/${projectId}/tasks`,
    request,
  )
  return response.data
}

export async function createTaskWithOptionalPlan(request: {
  title: string
  projectId?: number | null
  priority?: boolean
  urgent?: boolean
  planDate?: string | null
}): Promise<TaskResponse> {
  const response = await client.post<TaskResponse>('/tasks', request)
  return response.data
}

export async function getTasks(projectId: number): Promise<TaskResponse[]> {
  const response = await client.get<TaskResponse[]>(`/projects/${projectId}/tasks`)
  return response.data
}

export async function getTaskList(
  mode: TaskListMode,
  signal?: AbortSignal,
): Promise<TaskResponse[]> {
  const response = await client.get<TaskResponse[]>('/tasks', {
    params: { mode },
    signal,
  })
  return response.data
}

export async function getTaskMatrixPage(
  section: TaskMatrixSection,
  options: { cursor?: string | null; size?: number; signal?: AbortSignal } = {},
): Promise<TaskMatrixPageResponse> {
  const response = await client.get<TaskMatrixPageResponse>('/tasks/matrix', {
    params: {
      section,
      size: options.size ?? 6,
      ...(options.cursor ? { cursor: options.cursor } : {}),
    },
    signal: options.signal,
  })
  return response.data
}

export async function updateTaskTitle(
  taskId: number,
  request: UpdateTaskTitleRequest,
): Promise<TaskResponse> {
  const response = await client.patch<TaskResponse>(`/tasks/${taskId}/title`, request)
  return response.data
}

export async function updateTaskStatus(
  taskId: number,
  request: UpdateTaskStatusRequest,
): Promise<TaskResponse> {
  const response = await client.patch<TaskResponse>(`/tasks/${taskId}/status`, request)
  return response.data
}

export async function updateTaskPriority(
  taskId: number,
  request: UpdateTaskPriorityRequest,
): Promise<TaskResponse> {
  const response = await client.patch<TaskResponse>(`/tasks/${taskId}/priority`, request)
  return response.data
}

export async function updateTaskUrgent(
  taskId: number,
  request: UpdateTaskUrgentRequest,
): Promise<TaskResponse> {
  const response = await client.patch<TaskResponse>(`/tasks/${taskId}/urgent`, request)
  return response.data
}

export async function deleteTasks(request: DeleteTasksRequest): Promise<void> {
  await client.delete('/tasks', { data: request })
}
