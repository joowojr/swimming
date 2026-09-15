import { useEffect, useState } from 'react'
import type { SessionDetailResponse } from './sessionTypes'

/**
 * 역할: 진행 중인 세션의 남은 시간을 한 규칙으로 계산한다.
 * 핀보드 카드와 플로팅 버튼이 같은 값을 보여야 하고, 세션 화면과도 규칙이 같아야 한다.
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

/** 세션이 있을 때만 도는 시계. 없으면 타이머를 걸지 않는다. */
export function useSessionClock(sessionId: number | null) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (sessionId === null) return

    // 세션이 나중에 시작돼도 첫 프레임이 낡은 시각으로 남지 않게, 붙자마자 한 번 맞춘다.
    const sync = () => setNow(Date.now())
    const first = window.setTimeout(sync, 0)
    const timer = window.setInterval(sync, CLOCK_TICK_MS)

    return () => {
      window.clearTimeout(first)
      window.clearInterval(timer)
    }
  }, [sessionId])

  return now
}
