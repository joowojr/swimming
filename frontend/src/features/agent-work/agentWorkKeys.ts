import type { AgentWorkItem } from './agentWorkTypes'

/** 세션 시작 전후로 바뀌지 않는 카드 식별자. DB 등록 ID가 없는 Task도 구분한다. */
export function workItemKey(item: AgentWorkItem): string {
  return `${item.workItem.type}:${item.workItem.id}`
}
