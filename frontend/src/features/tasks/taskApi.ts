import { client } from '../../api/client'
import type {
  CreateTaskRequest,
  DeleteTasksRequest,
  ReorderTasksRequest,
  TaskResponse,
  UpdateTaskRequest,
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

export async function updateTask(
  taskId: number,
  request: UpdateTaskRequest,
): Promise<TaskResponse> {
  const response = await client.patch<TaskResponse>(`/tasks/${taskId}`, request)
  return response.data
}

export async function deleteTasks(request: DeleteTasksRequest): Promise<void> {
  await client.delete('/tasks', { data: request })
}

export async function reorderTasks(
  projectId: number,
  request: ReorderTasksRequest,
): Promise<void> {
  await client.put(`/projects/${projectId}/tasks/order`, request)
}
