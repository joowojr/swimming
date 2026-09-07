/** Knowledge Link API v0.4 §3·§4의 응답 모양. */

import type { CursorPage, CursorPageQuery } from '../../api/types'

export type SourceProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

/** 눌러서 Node Detail로 갈 수 있도록 이름과 함께 id를 받는다. */
export interface NodeRef {
  nodeId: string
  title: string
}

/**
 * Source 카드 한 장. 저장 응답과 목록 응답이 같은 모양을 공유한다.
 * status가 COMPLETED가 아니면 summary·topic·subjects는 비어 있다.
 */
export interface SourceCard {
  sourceId: string
  title: string
  url: string
  domain: string | null
  sourceType: string | null
  status: SourceProcessingStatus
  /** 이 폴더에 저장한 시각. */
  createdAt: string | null
  summary: string | null
  topic: NodeRef | null
  subjects: NodeRef[] | null
}

export type SourceListResponse = CursorPage<SourceCard>

export interface SourceListQuery extends CursorPageQuery {
  status?: SourceProcessingStatus
}

/** 링크를 어떻게 처리했는가. 그 문서의 소화가 어디까지 갔는지는 SourceCard.status다. */
export type SourceCollectResult = 'CREATED' | 'ALREADY_SAVED' | 'FAILED'

export type SourceFetchFailure =
  | 'INVALID_URL'
  | 'BLOCKED_ADDRESS'
  | 'UNSUPPORTED_CONTENT_TYPE'
  | 'HTTP_ERROR'
  | 'TIMEOUT'
  | 'EMPTY_CONTENT'
  | 'UNKNOWN'

export interface SourceCollectItem {
  url: string
  result: SourceCollectResult
  source: SourceCard | null
  reason: SourceFetchFailure | null
}

export interface SourceCollectResponse {
  items: SourceCollectItem[]
}
