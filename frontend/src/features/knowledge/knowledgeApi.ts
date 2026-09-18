import { client } from '../../api/client'
import type {
  NodeRef,
  SourceCollectResponse,
  SourceDeleteResponse,
  SourceListQuery,
  SourceListResponse,
} from './knowledgeTypes'

/** 노드 이름만 수정한다. */
export async function updateNodeTitle(nodeId: string, title: string): Promise<NodeRef> {
  const response = await client.patch<NodeRef>(`/knowledge/nodes/${nodeId}/title`, { title })
  return response.data
}

/** 지정한 기존 카테고리로 문서 한 건 또는 전체를 옮긴다. */
export async function mergeCategory(nodeId: string, targetCategoryId: string, sourceId?: string): Promise<NodeRef> {
  const response = await client.put<NodeRef>(`/knowledge/nodes/${nodeId}/merge`, { targetCategoryId, sourceId })
  return response.data
}

export async function getSources(
  folderId: number,
  query: SourceListQuery = {},
): Promise<SourceListResponse> {
  const response = await client.get<SourceListResponse>(
    `/folders/${folderId}/knowledge/sources`,
    { params: query },
  )
  return response.data
}

/**
 * 링크를 저장하는 API는 이것 하나다. 수집과 소화를 나누어 부르지 않는다.
 * 링크마다의 결과가 items로 돌아오고, 하나가 실패해도 나머지는 저장된다.
 */
export async function collectSources(
  folderId: number,
  urls: string[],
): Promise<SourceCollectResponse> {
  const response = await client.post<SourceCollectResponse>(
    `/folders/${folderId}/knowledge/sources`,
    { urls },
  )
  return response.data
}

/** 문서를 지운다. 딸린 주제도 함께 사라지고, 키워드는 다른 문서가 쓰므로 남는다. */
export async function deleteSource(sourceId: string): Promise<SourceDeleteResponse> {
  const response = await client.delete<SourceDeleteResponse>(`/knowledge/sources/${sourceId}`)
  return response.data
}

/**
 * 링크를 읽음으로 표시한다. 읽은 시각은 서버가 정하므로 보낼 것이 없다.
 * 토글이 아니라 상태를 지정하는 것이라 같은 요청이 겹쳐도 결과가 같다.
 */
export async function markSourceRead(sourceId: string): Promise<void> {
  await client.post(`/knowledge/sources/${sourceId}/read`)
}

/** 읽음 표시를 되돌린다. */
export async function markSourceUnread(sourceId: string): Promise<void> {
  await client.delete(`/knowledge/sources/${sourceId}/read`)
}

/** 이미 수집한 본문으로 LLM 분석만 다시 실행한다. */
export async function retrySource(sourceId: string): Promise<import('./knowledgeTypes').SourceCard> {
  const response = await client.post<import('./knowledgeTypes').SourceCard>(
    `/knowledge/sources/${sourceId}/retry`,
  )
  return response.data
}
