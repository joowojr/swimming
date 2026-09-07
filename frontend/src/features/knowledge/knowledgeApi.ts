import { client } from '../../api/client'
import type {
  SourceCollectResponse,
  SourceListQuery,
  SourceListResponse,
} from './knowledgeTypes'

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

/** 문서를 지운다. 딸린 목적도 함께 사라지고, 개념은 다른 문서가 쓰므로 남는다. */
export async function deleteSource(sourceId: string): Promise<void> {
  await client.delete(`/knowledge/sources/${sourceId}`)
}
