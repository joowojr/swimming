/** Knowledge Link API v0.4 §3·§4의 응답 모양. */

import type { CursorPage, CursorPageQuery } from '../../api/types'

export type SourceProcessingStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SOURCE_NOT_DIGEST'
  | 'COMPLETED'
  | 'FAILED'

export type SourceFailureCode =
  | 'SOURCE_INVALID_URL'
  | 'SOURCE_BLOCKED_ADDRESS'
  | 'SOURCE_UNSUPPORTED_CONTENT_TYPE'
  | 'SOURCE_HTTP_ERROR'
  | 'SOURCE_TIMEOUT'
  | 'SOURCE_UNKNOWN'
  | 'SOURCE_EMPTY_CONTENT'
  | 'SOURCE_DIGEST_FAILURE'
  | 'SOURCE_DIGEST_NON_RETRYABLE_FAILURE'
  | 'SOURCE_DIGEST_RESOLUTION_FAILURE'
  | 'SOURCE_DIGEST_RESOLUTION_NON_RETRYABLE_FAILURE'

/** 눌러서 Node Detail로 갈 수 있도록 이름과 함께 id를 받는다. */
export interface NodeRef {
  nodeId: string
  title: string
}

/**
 * Source 카드 한 장. 저장 응답과 목록 응답이 같은 모양을 공유한다.
 * SOURCE_NOT_DIGEST이면 content에 100자 이하 원문이 담기고, LLM 분석 필드는 비어 있다.
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
  /** 읽은 시각. 아직 읽지 않았으면 null. 시각은 서버가 정한다. */
  readAt: string | null
  /** 실패했을 때 서버가 제공하는 SOURCE_* 오류 코드. */
  failureMessage: SourceFailureCode | null
  /** 같은 분석을 다시 시도할 수 있는지. */
  retryable: boolean
  summary: string | null
  content: string | null
  topic: NodeRef | null
  subjects: NodeRef[] | null
  /** 현재 소속 카테고리. 배정되지 않았거나 삭제된 카테고리면 null. */
  category: NodeRef | null
}

export type SourceListResponse = CursorPage<SourceCard>

export interface SourceListQuery extends CursorPageQuery {
  status?: SourceProcessingStatus
}

/** 링크를 어떻게 처리했는가. 그 문서의 소화가 어디까지 갔는지는 SourceCard.status다. */
export type SourceCollectResult = 'CREATED' | 'ALREADY_SAVED' | 'FAILED'

export interface SourceCollectItem {
  url: string
  result: SourceCollectResult
  source: SourceCard | null
  failureMessage: SourceFailureCode | null
  retryable: boolean
}

export interface SourceCollectResponse {
  items: SourceCollectItem[]
}

export interface SourceDeleteResponse {
  folderId: number
  hasSource: boolean
}
