import { client } from '../../../api/client'
import type { GraphQuery, GraphResponse } from './graphTypes'

/**
 * Folder를 진입점으로 하는 초기 서브그래프.
 * Folder의 모든 Source를 펼치지 않고 limit에서 자른다(UX §6.2).
 */
export async function getFolderGraph(
  folderId: number,
  query: GraphQuery = {},
): Promise<GraphResponse> {
  const response = await client.get<GraphResponse>('/knowledge/graph', {
    params: { folderId, ...query },
  })
  return response.data
}
