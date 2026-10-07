import { AGENT_NAMES, formatClockTime } from './agentWorkLabels'
import { workItemKey } from './agentWorkKeys'
import type { AgentSession, AgentWorkItem, BoardLane } from './agentWorkTypes'

/**
 * Lane에 놓이는 카드 하나.
 * 시작 전 Lane은 할 일이 주체이고, 그 밖의 Lane은 Agent 세션이 주체다.
 * 보드 API는 할 일 단위로 응답하므로 세션 Lane은 화면에서 세션으로 묶는다.
 */
export type BoardEntry =
  | { kind: 'workItem'; key: string; card: AgentWorkItem }
  | { kind: 'session'; key: string; session: AgentSession; cards: AgentWorkItem[] }

export function sessionKey(sessionId: number): string {
  return `session:${sessionId}`
}

/** 세션을 부르는 이름. 시작할 때 받은 지시문의 첫 줄을 쓰고, 없으면 Agent와 시작 시각으로 부른다. */
export function sessionTitle(session: AgentSession): string {
  const firstLine = session.instruction?.split('\n').map((line) => line.trim()).find((line) => line.length > 0)
  return firstLine ?? `${AGENT_NAMES[session.agentType]} · ${formatClockTime(session.startedAt)} 시작`
}

/**
 * Lane의 카드 목록. 세션 Lane에서는 같은 세션의 할 일을 한 카드로 묶는다.
 * 보드 API가 정해 준 순서를 그대로 쓰기 위해 세션이 처음 나온 자리에 카드를 둔다.
 */
export function toBoardEntries(lane: BoardLane, cards: AgentWorkItem[]): BoardEntry[] {
  if (lane === 'NOT_STARTED') {
    return cards.map((card) => ({ kind: 'workItem', key: workItemKey(card), card }))
  }

  const entries: BoardEntry[] = []
  const indexBySessionId = new Map<number, number>()
  cards.forEach((card) => {
    const session = card.session
    if (!session) {
      entries.push({ kind: 'workItem', key: workItemKey(card), card })
      return
    }
    const index = indexBySessionId.get(session.id)
    if (index === undefined) {
      indexBySessionId.set(session.id, entries.length)
      entries.push({ kind: 'session', key: sessionKey(session.id), session, cards: [card] })
      return
    }
    const entry = entries[index]
    if (entry.kind === 'session') entry.cards.push(card)
  })
  return entries
}
