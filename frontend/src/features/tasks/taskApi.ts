import { client } from '../../api/client'
import type { CursorPage, CursorPageQuery } from '../../api/types'
import type {
  CreateTasksBatchRequest,
  DeleteTasksRequest,
  TaskResponse,
  UpdateTaskStatusRequest,
  UpdateTaskInfoRequest,
  UpdateTaskInfoResponse,
  UpdateTaskTitleRequest,
  UpdateTaskPriorityRequest,
  UpdateTaskUrgentRequest,
  TaskMatrixPageResponse,
  TaskMatrixSection,
  TaskSort,
  TaskStatus,
  TaskSummaryResponse,
  TaskPlacementRequest,
  TaskPlacementResponse,
} from './taskTypes'

export async function createTaskWithOptionalPlan(request: {
  title: string
  folderId?: number | null
  priority?: boolean
  urgent?: boolean
  planDate?: string | null
}): Promise<TaskResponse> {
  const response = await client.post<TaskResponse>('/tasks', request)
  return response.data
}

/**
 * 모달에서 담은 할 일을 한 번에 만든다. 서버가 한 트랜잭션으로 처리하므로
 * 하나라도 실패하면 아무것도 만들어지지 않는다.
 */
export async function createTasksBatch(
  request: CreateTasksBatchRequest,
): Promise<TaskResponse[]> {
  const response = await client.post<TaskResponse[]>('/tasks/batch', request)
  return response.data
}

/** 폴더에 담긴 할 일. 폴더 정보는 getFolder가 따로 준다. */
export async function getFolderTasks(
  folderId: number,
  query: CursorPageQuery = {},
): Promise<CursorPage<TaskSummaryResponse>> {
  const response = await client.get<CursorPage<TaskSummaryResponse>>(
    `/folders/${folderId}/tasks`,
    { params: query },
  )
  return response.data
}

export async function getTaskList(
  sort: TaskSort,
  signal?: AbortSignal,
): Promise<TaskResponse[]> {
  const response = await client.get<TaskResponse[]>('/tasks', {
    params: { sort },
    signal,
  })
  return response.data
}

export async function getTaskMatrixPage(
  section: TaskMatrixSection,
  options: { cursor?: string | null; size?: number; status?: TaskStatus; signal?: AbortSignal } = {},
): Promise<TaskMatrixPageResponse> {
  const response = await client.get<TaskMatrixPageResponse>('/tasks/matrix', {
    params: {
      section,
      size: options.size ?? 6,
      ...(options.cursor ? { cursor: options.cursor } : {}),
      // 상태 필터는 커서 윈도잉 앞에서 걸려야 페이지네이션이 성립한다.
      ...(options.status ? { status: options.status } : {}),
    },
    signal: options.signal,
  })
  return response.data
}

export async function moveTask(
  taskId: number,
  request: TaskPlacementRequest,
): Promise<TaskPlacementResponse> {
  const response = await client.patch<TaskPlacementResponse>(`/tasks/${taskId}/placement`, request)
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

/** @deprecated updateTaskInfo(taskId, { priority })를 쓴다. */
export async function updateTaskPriority(
  taskId: number,
  request: UpdateTaskPriorityRequest,
): Promise<TaskResponse> {
  const response = await client.patch<TaskResponse>(`/tasks/${taskId}/priority`, request)
  return response.data
}

/** @deprecated updateTaskInfo(taskId, { urgent })를 쓴다. */
export async function updateTaskUrgent(
  taskId: number,
  request: UpdateTaskUrgentRequest,
): Promise<TaskResponse> {
  const response = await client.patch<TaskResponse>(`/tasks/${taskId}/urgent`, request)
  return response.data
}

export async function updateTaskInfo(
  taskId: number,
  request: UpdateTaskInfoRequest,
): Promise<UpdateTaskInfoResponse> {
  const response = await client.patch<UpdateTaskInfoResponse>(`/tasks/${taskId}/info`, request)
  return response.data
}

export async function deleteTasks(request: DeleteTasksRequest): Promise<void> {
  await client.delete('/tasks', { data: request })
}
