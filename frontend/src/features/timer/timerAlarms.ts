import type { SessionDetailResponse } from '../sessions/sessionTypes'
import type { FreeTimer } from '../../store/timerStore'

/** 이 간격 안에 모인 알림은 한 번만 울린다. */
const MERGE_WINDOW_MS = 1000

type SessionTiming = Pick<
  SessionDetailResponse,
  'startedAt' | 'plannedDurationSec' | 'focusDurationSec' | 'breakDurationSec' | 'repeatCount'
>

/**
 * 역할: 알림음을 울릴 시각을 모은다. 화면도 소리도 모르는 계산만 한다.
 *
 * 독립 타이머는 끝나는 시각 하나, 세션은 집중 → 휴식·휴식 → 집중 전환과 완료 시각이다.
 * 세션 화면의 timerPhase와 같은 규칙으로 센다 — i번째 집중은 시작 + i·집중 + (i−1)·휴식에
 * 끝나고, i번째 휴식은 시작 + i·집중 + i·휴식에 끝난다. 휴식이 없으면 전환도 없다.
 *
 * 지난 시각도 함께 돌려준다. 걸러 내는 것은 예약하는 쪽의 몫이다 — 렌더링 시각에 따라
 * 결과가 달라지면 같은 타이머인데도 예약을 다시 걸게 된다.
 *
 * @return 울릴 시각(epoch ms), 오름차순
 */
export function timerAlarmTimes(
  timer: FreeTimer | null,
  session: SessionTiming | null,
): number[] {
  const times: number[] = []

  if (timer) {
    times.push(Date.parse(timer.startedAt) + timer.plannedDurationSec * 1000)
  }

  if (session) {
    const startedAt = Date.parse(session.startedAt)
    const focusMs = (session.focusDurationSec || session.plannedDurationSec) * 1000
    const breakMs = (session.breakDurationSec || 0) * 1000
    const repeats = session.repeatCount || 1

    if (breakMs > 0) {
      for (let index = 1; index < repeats; index += 1) {
        times.push(startedAt + index * focusMs + (index - 1) * breakMs)
        times.push(startedAt + index * focusMs + index * breakMs)
      }
    }
    times.push(startedAt + session.plannedDurationSec * 1000)
  }

  const sorted = times.filter(Number.isFinite).sort((a, b) => a - b)

  const merged: number[] = []
  for (const time of sorted) {
    if (merged.length === 0 || time - merged[merged.length - 1] >= MERGE_WINDOW_MS) merged.push(time)
  }
  return merged
}
