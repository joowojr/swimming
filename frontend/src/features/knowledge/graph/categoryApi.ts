import { client } from '../../../api/client'
import type {
  CategoryPreviewRequest,
  CategoryPreviewResponse,
  CategoryReplaceRequest,
  CategoryReplaceResponse,
} from './categoryTypes'

/**
 * 묶음 초안을 만든다. 노드도 관계도 저장하지 않는다.
 *
 * 읽기처럼 보이지만 GET이 아니다. LLM을 호출하는 비싼 작업이고 같은 폴더에 두 번 불러도
 * 결과가 다를 수 있어 캐시되면 안 된다.
 */
export async function previewCategories(
  folderId: number,
  request: CategoryPreviewRequest,
): Promise<CategoryPreviewResponse> {
  const response = await client.post<CategoryPreviewResponse>(
    `/folders/${folderId}/knowledge/categories/preview`,
    request,
  )
  return response.data
}

/**
 * 폴더의 Category 구성을 통째로 바꾼다.
 *
 * 기존 Category는 soft delete되고 요청 항목이 전부 새로 생긴다. 응답의 nodeId로
 * 배지·그래프를 다시 그린다.
 */
export async function replaceCategories(
  folderId: number,
  request: CategoryReplaceRequest,
): Promise<CategoryReplaceResponse> {
  const response = await client.put<CategoryReplaceResponse>(
    `/folders/${folderId}/knowledge/categories`,
    request,
  )
  return response.data
}
