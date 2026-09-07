import type { SourceFetchFailure, SourceProcessingStatus } from './knowledgeTypes'

/** 소화가 어디까지 갔는지. COMPLETED는 별도 표시가 필요 없어 카드에서 배지를 그리지 않는다. */
export const SOURCE_STATUS_LABEL: Record<SourceProcessingStatus, string> = {
  PENDING: '정리를 기다리는 중',
  PROCESSING: '내용을 정리하는 중',
  COMPLETED: '정리 완료',
  FAILED: '요약을 가져오지 못했어요',
}

export const SOURCE_FETCH_FAILURE_LABEL: Record<SourceFetchFailure, string> = {
  INVALID_URL: '링크 주소를 확인해 주세요.',
  BLOCKED_ADDRESS: '가져올 수 없는 주소예요.',
  UNSUPPORTED_CONTENT_TYPE: '웹 문서가 아니라 가져오지 못했어요.',
  HTTP_ERROR: '문서를 여는 데 실패했어요.',
  TIMEOUT: '응답이 늦어 가져오지 못했어요.',
  EMPTY_CONTENT: '가져올 본문이 없었어요.',
  UNKNOWN: '링크를 가져오지 못했어요.',
}
