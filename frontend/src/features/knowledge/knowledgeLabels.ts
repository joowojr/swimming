import type { SourceProcessingStatus } from './knowledgeTypes'

/** 소화가 어디까지 갔는지. COMPLETED는 별도 표시가 필요 없어 카드에서 배지를 그리지 않는다. */
export const SOURCE_STATUS_LABEL: Record<SourceProcessingStatus, string> = {
  PENDING: '정리를 기다리는 중',
  PROCESSING: '내용을 정리하는 중',
  COMPLETED: '정리 완료',
  FAILED: '요약을 가져오지 못했어요',
}
