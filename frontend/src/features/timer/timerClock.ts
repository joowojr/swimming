import { useEffect, useState } from 'react'

/**
 * 역할: 무엇을 재든 같은 한 가지 규칙으로 남은 시간을 센다.
 *
 * 남은 시간 = 정한 길이 - (현재 시각 - 시작 시각). 세션도 할 일도 이 규칙이 같아서,
 * 재는 쪽이 아니라 세는 규칙이 여기 산다.
 */

/**
 * 남은 시간을 분:초로 센다. 정한 길이를 넘기면 앞에 +를 붙여 더 진행한 시간을 센다.
 * 세션이 아니라 시작 시각과 길이를 받으므로, 시작과 길이가 있는 것이면 무엇이든 셀 수 있다.
 */
export function formatCountdown(startedAt: string, plannedDurationSec: number, now: number) {
  const elapsedSec = Math.max(0, Math.floor((now - Date.parse(startedAt)) / 1000))
  const remainingSec = plannedDurationSec - elapsedSec
  const shown = Math.abs(remainingSec)

  return `${remainingSec < 0 ? '+' : ''}${Math.floor(shown / 60)}:${String(shown % 60).padStart(2, '0')}`
}

/**
 * 지난 만큼을 0~1로. 정한 길이를 넘겨도 1을 넘지 않는다 — 링은 한 바퀴가 끝이고,
 * 더 갔다는 것은 숫자 앞의 +가 말한다.
 */
export function countdownProgress(startedAt: string, plannedDurationSec: number, now: number) {
  if (plannedDurationSec <= 0) return 1

  const elapsedSec = Math.max(0, Math.floor((now - Date.parse(startedAt)) / 1000))
  return Math.min(1, elapsedSec / plannedDurationSec)
}

/**
 * 셀 것이 있을 때만 도는 시계. 없으면 타이머를 걸지 않는다.
 *
 * key는 지금 재고 있는 대상이다. 바뀌면 시계를 다시 맞춘다. 주기는 보여주는 단위가
 * 정한다 — 분만 보여주는 화면이 매초 다시 그릴 이유가 없다.
 */
export function useCountdownClock(key: string | number | null, tickMs: number) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (key === null) return

    // 나중에 시작돼도 첫 프레임이 낡은 시각으로 남지 않게, 붙자마자 한 번 맞춘다.
    const sync = () => setNow(Date.now())
    const first = window.setTimeout(sync, 0)
    const timer = window.setInterval(sync, tickMs)

    return () => {
      window.clearTimeout(first)
      window.clearInterval(timer)
    }
  }, [key, tickMs])

  return now
}
