import { useEffect, useMemo, useState } from 'react'
import {
  IconArrowLeft,
  IconBellOff,
  IconBuilding,
  IconCheck,
  IconChecklist,
  IconClock,
  IconFlag,
  IconLoader2,
  IconLock,
  IconMusic,
  IconUsers,
} from '@tabler/icons-react'
import { useNavigate, useParams } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import { endSession, getActiveSession } from './sessionApi'
import type { ActiveSessionResponse, SessionResponse } from './sessionTypes'
import SessionMusicPlayer from './music/SessionMusicPlayer'
import styles from './PersonalSessionPage.module.css'

type PageState =
  | { status: 'loading' }
  | { status: 'ready'; session: ActiveSessionResponse }
  | { status: 'ended'; session: ActiveSessionResponse; result: SessionResponse }
  | { status: 'empty' }
  | { status: 'error'; message: string }

function remainingSeconds(session: ActiveSessionResponse) {
  const elapsed = Math.max(0, Math.floor((Date.now() - Date.parse(session.startedAt)) / 1000))
  return Math.max(0, session.plannedDurationSec - elapsed)
}

function formatTimer(totalSeconds: number) {
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return hours > 0
    ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function formatMinutes(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const remaining = seconds % 60
  return remaining === 0 ? `${minutes}분` : `${minutes}분 ${remaining}초`
}

function errorMessage(error: unknown) {
  const apiError = error as ApiError
  return apiError.message ?? '진행 중인 세션을 불러오지 못했습니다.'
}

export default function PersonalSessionPage() {
  const navigate = useNavigate()
  const { sessionId } = useParams()
  const [state, setState] = useState<PageState>({ status: 'loading' })
  const [nowKey, setNowKey] = useState(0)
  const [isEnding, setIsEnding] = useState(false)
  const [requestKey, setRequestKey] = useState(0)
  const [isMusicOpen, setIsMusicOpen] = useState(true)
  const [musicSource, setMusicSource] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void getActiveSession()
      .then((session) => {
        if (!active) return
        setState(!session || String(session.id) !== sessionId
          ? { status: 'empty' }
          : { status: 'ready', session })
      })
      .catch((error: unknown) => {
        if (active) setState({ status: 'error', message: errorMessage(error) })
      })
    return () => { active = false }
  }, [requestKey, sessionId])

  useEffect(() => {
    if (state.status !== 'ready') return
    const update = () => setNowKey((key) => key + 1)
    const intervalId = window.setInterval(update, 1000)
    document.addEventListener('visibilitychange', update)
    return () => {
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', update)
    }
  }, [state.status])

  const session = state.status === 'ready' || state.status === 'ended' ? state.session : null
  const remaining = useMemo(
    () => session ? remainingSeconds(session) : 0,
    // nowKey intentionally triggers calculation from the absolute start time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [session, nowKey],
  )
  const progress = session
    ? Math.min(1, Math.max(0, 1 - remaining / session.plannedDurationSec))
    : 0
  const ringOffset = 276.46 * (1 - progress)
  const currentTask = session?.tasks[0]
  const nextTasks = session?.tasks.slice(1) ?? []

  const finish = async () => {
    if (!session || isEnding) return
    setIsEnding(true)
    try {
      const result = await endSession(session.id)
      setState({ status: 'ended', session, result })
    } catch (error) {
      setState({ status: 'error', message: errorMessage(error) })
    } finally {
      setIsEnding(false)
    }
  }

  if (state.status === 'loading') {
    return <main className={styles.state}><IconLoader2 className={styles.spinner} /><p>진행 중인 세션을 불러오는 중…</p></main>
  }

  if (state.status === 'empty' || state.status === 'error') {
    return (
      <main className={styles.state}>
        <IconClock aria-hidden="true" />
        <h1>{state.status === 'empty' ? '진행 중인 세션이 없습니다.' : '세션을 불러오지 못했습니다.'}</h1>
        {state.status === 'error' && <p role="alert">{state.message}</p>}
        <div className={styles['state-actions']}>
          {state.status === 'error' && <button type="button" onClick={() => {
            setState({ status: 'loading' })
            setRequestKey((key) => key + 1)
          }}>다시 불러오기</button>}
          <button type="button" onClick={() => navigate('/projects')}>내 프로젝트로</button>
        </div>
      </main>
    )
  }

  if (state.status === 'ended') {
    return (
      <main className={`${styles.page} ${styles['is-ended']}`}>
        <div className={styles.scene} aria-hidden="true"><span /><span /><span /></div>
        <section className={styles['ended-card']} aria-labelledby="session-ended-title">
          <span className={styles['ended-icon']}><IconCheck aria-hidden="true" /></span>
          <p>세션 마무리</p>
          <h1 id="session-ended-title">오늘의 집중을 잘 마무리했어요.</h1>
          <p>함께한 Task와 집중 기록을 저장했습니다.</p>
          <button type="button" onClick={() => navigate('/projects')}>내 프로젝트로 돌아가기</button>
        </section>
      </main>
    )
  }

  return (
    <main className={styles.page} aria-label="개인 세션 진행">
      <div className={styles.scene} aria-hidden="true"><span /><span /><span /></div>

      <nav className={styles.dock} aria-label="세션 위젯">
        <button type="button" disabled aria-label="공간 · 다음 기능"><IconBuilding /></button>
        <button type="button" aria-pressed="true" aria-label="할 일"><IconChecklist /></button>
        <button type="button" aria-pressed="true" aria-label="타이머"><IconClock /></button>
        <button type="button" disabled aria-label="함께하는 사람 · 그룹 세션 기능"><IconUsers /></button>
        <button
          type="button"
          aria-pressed={isMusicOpen}
          aria-label={isMusicOpen ? '음악 위젯 접기' : '음악 위젯 펼치기'}
          onClick={() => setIsMusicOpen((open) => !open)}
        ><IconMusic /></button>
        <button type="button" disabled aria-label="집중 모드 · 다음 기능"><IconBellOff /></button>
      </nav>

      <div className={`${styles.widget} ${styles.place}`}>
        <IconBuilding aria-hidden="true" />
        <div><strong>개인 세션</strong><span>공간은 다음 단계에서 선택</span></div>
        <small><IconLock aria-hidden="true" /> 준비 중</small>
      </div>

      <button className={`${styles.widget} ${styles.exit}`} type="button" onClick={() => navigate('/projects')}>
        <IconArrowLeft aria-hidden="true" /> 나가기
      </button>

      <section className={`${styles.widget} ${styles.tasks}`} aria-labelledby="current-task-title">
        <p>지금 하는 일</p>
        <div className={styles['current-task']}>
          <span aria-hidden="true" />
          <div><h1 id="current-task-title">{currentTask?.title}</h1><p>{currentTask?.projectName}</p></div>
        </div>
        {nextTasks.length > 0 && (
          <ol className={styles['next-tasks']} aria-label="다음 Task">
            {nextTasks.map((task) => <li key={task.id}>다음 · {task.title}</li>)}
          </ol>
        )}
      </section>

      <section className={`${styles.widget} ${styles.timer}`} aria-labelledby="session-timer-title">
        <header><span>개인 집중</span><strong>{remaining === 0 ? '시간 완료' : '집중'}</strong></header>
        <div className={styles['timer-main']}>
          <svg viewBox="0 0 100 100" aria-hidden="true">
            <circle className={styles['ring-track']} cx="50" cy="50" r="44" />
            <circle className={styles['ring-progress']} cx="50" cy="50" r="44" style={{ strokeDashoffset: ringOffset }} />
          </svg>
          <div><h2 id="session-timer-title">{formatTimer(remaining)}</h2><p>{formatMinutes(state.session.plannedDurationSec)} 중</p></div>
        </div>
        <p className={styles['timer-note']}>{remaining === 0 ? '정한 시간을 채웠습니다. 준비되면 세션을 마쳐 주세요.' : `${state.session.tasks.length}개 Task와 함께 집중하고 있습니다.`}</p>
        <button className={styles.finish} type="button" disabled={isEnding} onClick={() => void finish()}>
          {isEnding ? <IconLoader2 className={styles.spinner} /> : <IconFlag />}
          {isEnding ? '기록 중…' : '세션 마치기'}
        </button>
      </section>

      <section className={`${styles.widget} ${styles.people}`} aria-label="함께하는 사람">
        <header><span>함께하는 사람</span><small>개인 세션</small></header>
        <div className={styles.avatar}>나</div>
        <p>현재 목표는 나에게만 표시됩니다.</p>
      </section>

      {isMusicOpen && (
        <SessionMusicPlayer
          className={styles.music}
          source={musicSource}
          onSourceChange={setMusicSource}
        />
      )}

      <div className={styles['focus-status']}><IconBellOff aria-hidden="true" /> 세션 진행 중</div>
    </main>
  )
}
