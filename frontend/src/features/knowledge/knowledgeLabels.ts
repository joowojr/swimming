import type { SourceFailureCode, SourceProcessingStatus } from './knowledgeTypes'

/** 소화가 어디까지 갔는지. COMPLETED는 별도 표시가 필요 없어 카드에서 배지를 그리지 않는다. */
export const SOURCE_STATUS_LABEL: Record<SourceProcessingStatus, string> = {
  PENDING: '정리를 기다리는 중',
  PROCESSING: '내용을 정리하는 중',
  COMPLETED: '정리 완료',
  FAILED: '요약을 가져오지 못했어요',
}

const SOURCE_FAILURE_MESSAGE: Record<SourceFailureCode, string> = {
  SOURCE_INVALID_URL: '링크 주소를 다시 한 번 확인해 주세요.',
  SOURCE_BLOCKED_ADDRESS: '가져올 수 없는 주소예요.',
  SOURCE_UNSUPPORTED_CONTENT_TYPE: '웹 문서가 아니라 가져오지 못했어요.',
  SOURCE_HTTP_ERROR: '문서를 여는 데 실패했어요.',
  SOURCE_TIMEOUT: '응답이 늦어 가져오지 못했어요.',
  SOURCE_UNKNOWN: '링크를 가져오지 못했어요.',
  SOURCE_EMPTY_CONTENT: '문서에서 정리할 내용을 찾지 못했어요. 링크는 그대로 저장되어 있어요.',
  SOURCE_DIGEST_FAILURE: '문서 내용을 정리하지 못했어요. 잠시 후 다시 분석해 주세요.',
  SOURCE_DIGEST_NON_RETRYABLE_FAILURE: '문서 내용을 정리할 수 없어요. 링크는 그대로 저장되어 있어요.',
  SOURCE_DIGEST_RESOLUTION_FAILURE: '일시적인 오류가 발생했어요. 잠시 후 다시 분석해 주세요.',
  SOURCE_DIGEST_RESOLUTION_NON_RETRYABLE_FAILURE: '문서의 개념을 연결할 수 없어요. 링크는 그대로 저장되어 있어요.',
}

export function sourceFailureMessage(
  code: SourceFailureCode | null,
  fallback: string,
): string {
  return code ? SOURCE_FAILURE_MESSAGE[code] ?? fallback : fallback
}
