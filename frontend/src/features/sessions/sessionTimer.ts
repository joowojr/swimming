import { useCountdownClock } from '../timer/timerClock'
import type { SessionDetailResponse } from './sessionTypes'

/**
 * 역할: 세션의 남은 시간을 사람이 읽는 말로 옮긴다.
 * 핀보드 카드와 세션 화면이 같은 값을 보여야 한다.
 *
 * 세는 규칙 자체는 features/timer가 갖는다. 세션만의 것이 아니기 때문이다.
 */

/** 분 단위로만 보여주므로 이 주기면 충분하다. */
const CLOCK_TICK_MS = 15000

/**
 * 남은 시간 = 정한 길이 - (현재 시각 - 시작 시각).
 * 정한 길이를 넘기면 남은 시간 대신 더 진행한 시간을 센다. 세션 화면과 같은 규칙이다.
 */
export function formatRemaining(session: SessionDetailResponse, now: number) {
  const elapsedSec = Math.max(0, Math.floor((now - Date.parse(session.startedAt)) / 1000))
  const remainingSec = session.plannedDurationSec - elapsedSec

  if (remainingSec <= 0) return `+${Math.floor(-remainingSec / 60)}분`
  return `${Math.ceil(remainingSec / 60)}분 남음`
}

/** 세션이 있을 때만 도는 시계. 분만 보여주는 쪽이라 주기가 느리다. */
export function useSessionClock(sessionId: number | null) {
  return useCountdownClock(sessionId, CLOCK_TICK_MS)
}
