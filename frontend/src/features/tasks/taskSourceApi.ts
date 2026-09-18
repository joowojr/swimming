import { client } from '../../api/client'
import type { AttachTaskSourcesRequest, TaskSource } from './taskSourceTypes'

/** 할 일에 연결한 링크. 그 사이 지워진 문서는 빠져서 온다. */
export async function getTaskSources(
  taskId: number,
  signal?: AbortSignal,
): Promise<TaskSource[]> {
  const response = await client.get<TaskSource[]>(`/tasks/${taskId}/sources`, { signal })
  return response.data
}

/** 저장해 둔 문서를 할 일에 연결한다. 같은 문서를 두 번 연결해도 결과가 같다. */
export async function attachTaskSources(
  taskId: number,
  sourceIds: string[],
): Promise<TaskSource[]> {
  const request: AttachTaskSourcesRequest = { sourceIds }
  const response = await client.post<TaskSource[]>(`/tasks/${taskId}/sources`, request)
  return response.data
}

/** 연결만 끊는다. 문서 자체는 남는다. */
export async function detachTaskSource(taskId: number, sourceId: string): Promise<void> {
  await client.delete(`/tasks/${taskId}/sources/${sourceId}`)
}
