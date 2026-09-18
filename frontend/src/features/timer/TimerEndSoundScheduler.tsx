import { useEffect } from 'react'
import { useActiveSessionStore } from '../../store/activeSessionStore'
import { useTimerStore } from '../../store/timerStore'
import { timerAlarmTimes } from './timerAlarms'
import { playTimerSound, preloadTimerSound, unlockTimerSound } from './timerSound'

/**
 * 역할: 독립 타이머와 진행 중인 세션이 정한 시각에 닿을 때 알림음을 울린다. 그리는 것은 없다.
 *
 * 로그인한 뒤의 모든 화면 위에 하나만 둔다. 세션 화면은 AppShell 밖이라 플로팅 위젯에 두면
 * 세션 화면에서 울리지 않는다.
 *
 * 1초마다 도는 화면 시계로 "넘었는지"를 보지 않고 시각마다 setTimeout을 한 번 건다.
 * 브라우저는 숨겨진 탭의 반복 타이머를 크게 늦추기 때문이다. 시각이 바뀌면(세션 집중 시간
 * 변경, 타이머 끝내기, 세션 종료) 예약을 모두 지우고 앞으로 올 것만 다시 건다.
 */
export default function TimerEndSoundScheduler() {
  const timer = useTimerStore((state) => state.timer)
  const session = useActiveSessionStore((state) => state.session)

  // 누를 때마다 오디오를 깨운다. 시작 버튼, Enter로 시작, 새로고침 뒤 첫 누름을 한 곳에서 받는다.
  useEffect(() => {
    window.addEventListener('pointerdown', unlockTimerSound)
    window.addEventListener('keydown', unlockTimerSound)
    return () => {
      window.removeEventListener('pointerdown', unlockTimerSound)
      window.removeEventListener('keydown', unlockTimerSound)
    }
  }, [])

  // 세션 객체는 음악 주소만 바뀌어도 새로 오므로, 시각이 같으면 다시 걸지 않게 문자열로 비교한다.
  const alarmsKey = timerAlarmTimes(timer, session).join(',')

  useEffect(() => {
    const now = Date.now()
    // 이미 지난 시각은 걸지 않는다. 새로고침 뒤 지나간 알림을 늦게 울리지 않는다.
    const upcoming = alarmsKey ? alarmsKey.split(',').map(Number).filter((time) => time > now) : []
    if (upcoming.length === 0) return

    preloadTimerSound()
    const timeouts = upcoming.map((time) => window.setTimeout(() => void playTimerSound(), time - now))

    return () => timeouts.forEach((timeout) => window.clearTimeout(timeout))
  }, [alarmsKey])

  return null
}
