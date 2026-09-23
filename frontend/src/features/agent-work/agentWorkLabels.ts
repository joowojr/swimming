import type { AgentBoard, AgentSessionEventType, AgentType, AgentWorkStatus, BoardLane } from './agentWorkTypes'

export interface LaneDefinition {
  lane: BoardLane
  key: keyof AgentBoard
  title: string
}

/** 화면에 놓이는 Lane 순서. */
export const LANES: LaneDefinition[] = [
  { lane: 'NOT_STARTED', key: 'notStarted', title: '시작 전' },
  { lane: 'WORKING', key: 'working', title: 'AI 작업 중' },
  { lane: 'WAITING', key: 'waiting', title: '내 판단 대기' },
  { lane: 'COMPLETED', key: 'completed', title: '완료' },
  { lane: 'ATTENTION', key: 'attention', title: '문제 발생' },
]

export const AGENT_NAMES: Record<AgentType, string> = {
  CLAUDE_CODE: 'Claude Code',
  CODEX: 'Codex',
}

export const STATUS_LABELS: Record<AgentWorkStatus, string> = {
  WORKING: '작업 중',
  WAITING: '내 판단 대기',
  COMPLETED: '완료',
  FAILED: '작업 실패',
  UNKNOWN: '상태 확인 필요',
}

export const EVENT_LABELS: Record<AgentSessionEventType, string> = {
  STARTED: '작업 시작',
  PROGRESS_REPORTED: '진행 보고',
  WAITING_FOR_USER: '판단 요청',
  COMPLETED: '작업 완료',
  FAILED: '작업 실패',
  SIGNAL_LOST: '신호가 끊김',
}

export const UNCATEGORIZED_LABEL = '미분류'

const MINUTE = 60_000
const HOUR = 60 * MINUTE

/** 카드와 Inspector의 시각 표기. 오늘은 상대 시각, 그 이전은 날짜로 보여준다. */
export function formatRelativeTime(iso: string, now = new Date()) {
  const time = new Date(iso)
  const elapsed = now.getTime() - time.getTime()
  if (elapsed < MINUTE) return '방금'
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)}분 전`

  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  if (time >= startOfToday) return `${Math.floor(elapsed / HOUR)}시간 전`

  const startOfYesterday = new Date(startOfToday.getTime() - 24 * HOUR)
  if (time >= startOfYesterday) return '어제'
  return `${time.getMonth() + 1}월 ${time.getDate()}일`
}

export function formatClockTime(iso: string) {
  const time = new Date(iso)
  return `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`
}
