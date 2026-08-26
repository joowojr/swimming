import { client } from '../../api/client'
import type {
  CreateTaskRequest,
  DeleteTasksRequest,
  TaskResponse,
  UpdateTaskStatusRequest,
  UpdateTaskTitleRequest,
} from './taskTypes'

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

export async function getTasks(projectId: number): Promise<TaskResponse[]> {
  const response = await client.get<TaskResponse[]>(`/projects/${projectId}/tasks`)
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

export async function deleteTasks(request: DeleteTasksRequest): Promise<void> {
  await client.delete('/tasks', { data: request })
}

