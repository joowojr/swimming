import { useEffect, useId, useState } from 'react'
import type { CSSProperties } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  IconArrowRight,
  IconChevronDown,
  IconChevronUp,
  IconClock,
  IconPlayerStopFilled,
} from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import { useRevealOnApproach } from '../../lib/useRevealOnApproach'
import { useActiveSessionStore } from '../../store/activeSessionStore'
import { useTimerStore } from '../../store/timerStore'
import type { DailyPlanItem } from '../calendar/dailyPlanTypes'
import { getTodayPlanItems } from '../calendar/todayPlan'
import CreateSessionModal from '../sessions/CreateSessionModal'
import TimerPanel from './TimerPanel'
import { countdownProgress, formatCountdown, useCountdownClock } from './timerClock'
import styles from './TimerWidget.module.css'

/** 초까지 보여주므로 매초 다시 그린다. */
const TICK_MS = 1000

/** 독립 타이머가 끝나는 시각. "14:30"처럼 24시간제 시·분만 보여 준다. */
const endTimeFormatter = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })

/** 세션 시작 모달의 id. 패널의 버튼이 aria-controls로 가리킨다. */
const SESSION_DIALOG_ID = 'timer-create-session-dialog'

/**
 * 역할: 지금 재고 있는 것의 남은 시간을 어느 화면에서나 보여주는 플로팅 타이머.
 *
 * 음악 위젯과 같은 모양·같은 규칙이다. 평소에는 원이고, 다가가면 옆으로 늘어나 바가 되며,
 * 바의 펼치기 버튼으로 위쪽에 패널을 연다. 독에 선 위젯들이 한 벌로 읽힌다.
 *
 * 접혀 있어도 시간은 숨기지 않는다. 흘깃 보는 것이 타이머의 존재 이유라, 원 안에 남은
 * 시간을 두고 무엇을 하던 중인지는 다가갔을 때 내준다.
 *
 * 재는 것은 진행 중인 세션, 아니면 세션과 무관하게 시작한 타이머(timerStore)다. 세션이
 * 있으면 세션만 보여 주고, 타이머는 뒤에서 그대로 흐르다가 세션이 끝나면 다시 보인다.
 * 세션이 없을 때 패널에서 분을 적어 타이머를 시작하거나 세션 시작 모달을 연다.
 *
 * 진행 중인 세션을 처음 불러오는 자리이기도 하다. 이 위젯은 셸이 떠 있는 내내 살아 있어
 * 어느 화면에서 들어오든 한 번은 지나간다. 이후 갱신은 세션을 다루는 화면이 맡는다.
 */
export default function TimerWidget() {
  const navigate = useNavigate()
  const panelId = useId()
  const session = useActiveSessionStore((state) => state.session)
  const status = useActiveSessionStore((state) => state.status)
  const loadActiveSession = useActiveSessionStore((state) => state.load)
  const markSessionCreated = useActiveSessionStore((state) => state.markCreated)
  const freeTimer = useTimerStore((state) => state.timer)
  const startTimer = useTimerStore((state) => state.start)
  const stopTimer = useTimerStore((state) => state.stop)

  const [isExpanded, setIsExpanded] = useState(false)
  const [todayTasks, setTodayTasks] = useState<DailyPlanItem[] | null>(null)
  const [isPreparingSession, setIsPreparingSession] = useState(false)
  const [sessionError, setSessionError] = useState<string | null>(null)

  // 세션이 있으면 세션만 센다. 독립 타이머는 세션이 끝난 뒤 다시 보인다.
  const measured = session ?? freeTimer
  const now = useCountdownClock(
    session ? `session-${session.id}` : freeTimer ? `timer-${freeTimer.startedAt}` : null,
    TICK_MS,
  )
  // 패널은 세션이 없을 때만 연다. 세션 중에는 세션 화면이 그 일을 한다.
  const isPanelOpen = isExpanded && !session

  // 패널을 펼친 동안에는 접을 수 없다. 접으면 방금 연 패널과 조작이 함께 사라진다.
  const { isOpen, ref, approachProps, pin } = useRevealOnApproach<HTMLElement>(isPanelOpen)

  useEffect(() => {
    if (status === 'idle') void loadActiveSession()
  }, [status, loadActiveSession])

  const countdown = measured
    ? formatCountdown(measured.startedAt, measured.plannedDurationSec, now)
    : null
  const isOvertime = countdown?.startsWith('+') ?? false
  // 칠은 CSS가 한다. 여기서는 얼마나 왔는지만 넘긴다.
  const progress = measured
    ? countdownProgress(measured.startedAt, measured.plannedDurationSec, now)
    : 0
  const state = !measured ? 'idle' : isOvertime ? 'overtime' : 'running'

  const title = session
    ? session.tasks[0]?.title ?? '개인 집중 세션'
    : freeTimer
      ? `${Math.round(freeTimer.plannedDurationSec / 60)}분 · ${endTimeFormatter.format(Date.parse(freeTimer.startedAt) + freeTimer.plannedDurationSec * 1000)}`
      : '타이머'

  const openSessionModal = async () => {
    if (isPreparingSession || todayTasks !== null) return
    setIsPreparingSession(true)
    setSessionError(null)
    try {
      setTodayTasks(await getTodayPlanItems())
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setSessionError(apiMessage ?? '오늘 캘린더를 불러오지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsPreparingSession(false)
    }
  }

  return (
    <section
      ref={ref}
      className={styles.widget}
      data-expanded={isPanelOpen}
      data-open={isOpen ? 'true' : undefined}
      data-state={state}
      aria-label="타이머"
      {...approachProps}
      onClick={() => {
        // 접혀 있으면 첫 누름은 펼치기다. 접힌 동안에는 원 말고 누를 것이 없다.
        if (!isOpen) pin()
      }}
    >
      {isPanelOpen && (
        <TimerPanel
          id={panelId}
          isTimerRunning={freeTimer !== null}
          sessionDialogId={SESSION_DIALOG_ID}
          isSessionModalOpen={todayTasks !== null}
          isPreparingSession={isPreparingSession}
          sessionError={sessionError}
          onStartTimer={(minutes) => {
            startTimer(minutes)
            setIsExpanded(false)
          }}
          onStartSession={() => void openSessionModal()}
        />
      )}

      <div className={styles.bar}>
        <span
          className={styles.dial}
          style={{ '--timer-progress': progress } as CSSProperties}
          role={countdown ? 'timer' : undefined}
          aria-label={countdown
            ? `${isOvertime ? '더 진행한 시간' : '남은 시간'} ${countdown}`
            : undefined}
        >
          {countdown ?? <IconClock size={20} stroke={1.8} aria-hidden="true" />}
        </span>
        <span className={styles.title}>
          {session && <span className={styles.eyebrow}>진행 중</span>}
          <span className={styles.label}>{title}</span>
        </span>

        {session ? (
          <Link
            className={styles.control}
            to={`/sessions/${session.id}`}
            aria-label={`${title} 세션 이어서 하기`}
            onClick={(event) => {
              // 접힌 채로 눌린 첫 누름은 펼치기다. 어디로 가는지 보여 주고 나서 보낸다.
              if (!isOpen) event.preventDefault()
            }}
          >
            <IconArrowRight size={16} aria-hidden="true" />
          </Link>
        ) : (
          <>
            {freeTimer && (
              <button
                type="button"
                className={`${styles.control} ${styles['control-stop']}`}
                aria-label="타이머 끝내기"
                onClick={stopTimer}
              >
                <IconPlayerStopFilled size={16} aria-hidden="true" />
              </button>
            )}
            <button
              type="button"
              className={styles.control}
              aria-expanded={isPanelOpen}
              aria-controls={panelId}
              aria-label={isPanelOpen ? '타이머 패널 접기' : '타이머 패널 펼치기'}
              onClick={() => setIsExpanded((expanded) => !expanded)}
            >
              {isPanelOpen
                ? <IconChevronDown size={16} aria-hidden="true" />
                : <IconChevronUp size={16} aria-hidden="true" />}
            </button>
          </>
        )}
      </div>

      {todayTasks !== null && (
        <CreateSessionModal
          todayTasks={todayTasks}
          onClose={() => setTodayTasks(null)}
          onStarted={(startedSession) => {
            setTodayTasks(null)
            setIsExpanded(false)
            markSessionCreated(startedSession)
            navigate(`/sessions/${startedSession.id}`)
          }}
        />
      )}
    </section>
  )
}
