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
  IconMusic,
  IconMinus,
  IconPlus,
  IconUsers,
} from '@tabler/icons-react'
import { useNavigate, useParams } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import { getCities } from '../places/placeApi'
import { endSession, getSession, updateSessionMusicUrl, updateSessionPlannedDuration } from './sessionApi'
import type { SessionDetailResponse } from './sessionTypes'
import SessionMusicPlayer from './music/SessionMusicPlayer'
import type { SessionMusicOption } from './music/SessionMusicPlayer'
import styles from './PersonalSessionPage.module.css'

type PageState =
  | { status: 'loading' }
  | { status: 'ready'; session: SessionDetailResponse }
  | { status: 'ended'; session: SessionDetailResponse }
  | { status: 'empty' }
  | { status: 'error'; message: string }

interface WidgetVisibility {
  place: boolean
  tasks: boolean
  timer: boolean
  people: boolean
  music: boolean
}

const INITIAL_WIDGET_VISIBILITY: WidgetVisibility = {
  place: true,
  tasks: true,
  timer: true,
  people: true,
  music: true,
}

const DEFAULT_SESSION_BACKGROUND_URL = '/lisbon_1.mp4'

function remainingSeconds(session: SessionDetailResponse) {
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
  return apiError.message ?? '세션을 불러오지 못했습니다.'
}

export default function PersonalSessionPage() {
  const navigate = useNavigate()
  const { sessionId } = useParams()
  const parsedSessionId = Number(sessionId)
  const validSessionId = Number.isSafeInteger(parsedSessionId) && parsedSessionId > 0
    ? parsedSessionId
    : null
  const [state, setState] = useState<PageState>({ status: 'loading' })
  const [nowKey, setNowKey] = useState(0)
  const [isEnding, setIsEnding] = useState(false)
  const [isAdjustingDuration, setIsAdjustingDuration] = useState(false)
  const [durationStepSec, setDurationStepSec] = useState(10)
  const [requestKey, setRequestKey] = useState(0)
  const [widgets, setWidgets] = useState<WidgetVisibility>(INITIAL_WIDGET_VISIBILITY)
  const [focusMode, setFocusMode] = useState(false)
  const [musicOptions, setMusicOptions] = useState<SessionMusicOption[]>([])
  const [hasBackgroundError, setHasBackgroundError] = useState(false)

  useEffect(() => {
    if (!validSessionId) return

    let active = true
    void getSession(validSessionId)
      .then((session) => {
        if (!active) return
        setHasBackgroundError(false)
        setState(session.status === 'IN_PROGRESS'
          ? { status: 'ready', session }
          : { status: 'ended', session })
      })
      .catch((error: unknown) => {
        if (!active) return
        const apiError = error as ApiError
        setState(apiError.status === 404
          ? { status: 'empty' }
          : { status: 'error', message: errorMessage(error) })
      })
    return () => { active = false }
  }, [requestKey, validSessionId])

  useEffect(() => {
    let active = true
    void getCities()
      .then((cities) => {
        if (!active) return
        const urls = new Set<string>()
        setMusicOptions(cities.flatMap((city) => city.places.flatMap((place) => {
          const url = place.defaultMusicUrl
          if (!url || urls.has(url)) return []
          urls.add(url)
          return [{ id: place.id, label: `${city.name} · ${place.name}`, url }]
        })))
      })
      .catch(() => {
        if (active) setMusicOptions([])
      })
    return () => { active = false }
  }, [])

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
  const backgroundAsset = session?.place.backgroundAsset
  const configuredBackgroundUrl = backgroundAsset?.url?.trim() || null
  const backgroundUrl = configuredBackgroundUrl && !hasBackgroundError
    ? configuredBackgroundUrl
    : DEFAULT_SESSION_BACKGROUND_URL
  const backgroundType = configuredBackgroundUrl && !hasBackgroundError
    ? backgroundAsset?.type
    : 'VIDEO'

  const toggleWidget = (widget: keyof WidgetVisibility) => {
    setWidgets((current) => ({ ...current, [widget]: !current[widget] }))
  }

  const toggleFocusMode = () => {
    setFocusMode((current) => {
      if (!current) {
        setWidgets((visibility) => ({ ...visibility, timer: true }))
      }
      return !current
    })
  }

  const finish = async () => {
    if (!session || isEnding) return
    setIsEnding(true)
    try {
      await endSession(session.id)
      setState({ status: 'ended', session })
    } catch (error) {
      setState({ status: 'error', message: errorMessage(error) })
    } finally {
      setIsEnding(false)
    }
  }

  const saveMusicSource = async (source: string | null) => {
    if (state.status !== 'ready') return
    await updateSessionMusicUrl(state.session.id, { musicUrl: source })
    setState((current) => current.status === 'ready'
      ? { ...current, session: { ...current.session, musicUrl: source } }
      : current)
  }

  const adjustDuration = async (direction: -1 | 1) => {
    if (state.status !== 'ready' || isAdjustingDuration) return
    const plannedDurationSec = Math.min(
      86400,
      Math.max(60, state.session.plannedDurationSec + direction * durationStepSec),
    )
    if (plannedDurationSec === state.session.plannedDurationSec) return

    setIsAdjustingDuration(true)
    try {
      await updateSessionPlannedDuration(state.session.id, { plannedDurationSec })
      setState((current) => current.status === 'ready'
        ? { ...current, session: { ...current.session, plannedDurationSec } }
        : current)
    } finally {
      setIsAdjustingDuration(false)
    }
  }

  if (state.status === 'loading') {
    return <main className={styles.state}><IconLoader2 className={styles.spinner} /><p>세션을 불러오는 중…</p></main>
  }

  if (!validSessionId || state.status === 'empty' || state.status === 'error') {
    return (
      <main className={styles.state}>
        <IconClock aria-hidden="true" />
        <h1>{!validSessionId || state.status === 'empty' ? '세션을 찾을 수 없습니다.' : '세션을 불러오지 못했습니다.'}</h1>
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
        <video
          className={styles['background-asset']}
          src={DEFAULT_SESSION_BACKGROUND_URL}
          aria-hidden="true"
          autoPlay
          muted
          loop
          playsInline
        />
        <div className={styles['background-shade']} aria-hidden="true" />
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
    <main className={`${styles.page} ${focusMode ? styles['is-focus-mode'] : ''}`} aria-label="개인 세션 진행">
      <div className={styles.scene} aria-hidden="true"><span /><span /><span /></div>
      {backgroundType === 'IMAGE' && (
        <img
          className={styles['background-asset']}
          src={backgroundUrl}
          alt=""
          onError={() => setHasBackgroundError(true)}
        />
      )}
      {backgroundType === 'VIDEO' && (
        <video
          className={styles['background-asset']}
          src={backgroundUrl}
          aria-hidden="true"
          autoPlay
          muted
          loop
          playsInline
          onError={() => setHasBackgroundError(true)}
        />
      )}
      <div className={styles['background-shade']} aria-hidden="true" />

      <nav className={styles.dock} aria-label="세션 위젯">
        <button type="button" aria-pressed={widgets.place && !focusMode} aria-label={widgets.place ? '공간 위젯 접기' : '공간 위젯 펼치기'} onClick={() => toggleWidget('place')}><IconBuilding /></button>
        <button type="button" aria-pressed={widgets.tasks && !focusMode} aria-label={widgets.tasks ? '할 일 위젯 접기' : '할 일 위젯 펼치기'} onClick={() => toggleWidget('tasks')}><IconChecklist /></button>
        <button type="button" aria-pressed={widgets.timer} aria-label={widgets.timer ? '타이머 위젯 접기' : '타이머 위젯 펼치기'} onClick={() => toggleWidget('timer')}><IconClock /></button>
        <button type="button" aria-pressed={widgets.people && !focusMode} aria-label={widgets.people ? '참여자 위젯 접기' : '참여자 위젯 펼치기'} onClick={() => toggleWidget('people')}><IconUsers /></button>
        <button type="button" aria-pressed={widgets.music && !focusMode} aria-label={widgets.music ? '음악 위젯 접기' : '음악 위젯 펼치기'} onClick={() => toggleWidget('music')}><IconMusic /></button>
        <button type="button" aria-pressed={focusMode} aria-label={focusMode ? '집중 모드 해제' : '집중 모드 켜기'} onClick={toggleFocusMode}><IconBellOff /></button>
      </nav>

      {widgets.place && !focusMode && (
        <div className={`${styles.widget} ${styles.place}`}>
          <IconBuilding aria-hidden="true" />
          <div><strong>{state.session.place.cityName}</strong><span>{state.session.place.name}</span></div>
        </div>
      )}

      <button className={`${styles.widget} ${styles.exit}`} type="button" onClick={() => navigate('/projects')}>
        <IconArrowLeft aria-hidden="true" /> 나가기
      </button>

      {widgets.tasks && !focusMode && (
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
      )}

      {widgets.timer && (
        <section className={`${styles.widget} ${styles.timer}`} aria-labelledby="session-timer-title">
          <header><span>개인 집중</span><strong>{remaining === 0 ? '시간 완료' : '집중'}</strong></header>
          <div className={styles['timer-main']}>
            <svg viewBox="0 0 100 100" aria-hidden="true">
              <circle className={styles['ring-track']} cx="50" cy="50" r="44" />
              <circle className={styles['ring-progress']} cx="50" cy="50" r="44" style={{ strokeDashoffset: ringOffset }} />
            </svg>
            <div className={styles['timer-value']}>
              <h2 id="session-timer-title">{formatTimer(remaining)}</h2>
              <p>{formatMinutes(state.session.plannedDurationSec)} 중</p>
            </div>
          </div>
          <div className={styles['timer-adjust']} aria-label="집중 시간 조절">
            <button
              type="button"
              aria-label={`집중 시간 ${formatMinutes(durationStepSec)} 줄이기`}
              disabled={isAdjustingDuration || state.session.plannedDurationSec <= 60}
              onClick={() => void adjustDuration(-1)}
            >
              <IconMinus aria-hidden="true" />
            </button>
            <label>
              <span className={styles['visually-hidden']}>시간 조절 단위</span>
              <select
                value={durationStepSec}
                disabled={isAdjustingDuration}
                onChange={(event) => setDurationStepSec(Number(event.target.value))}
              >
                <option value={10}>10초</option>
                <option value={300}>5분</option>
                <option value={600}>10분</option>
              </select>
            </label>
            <button
              type="button"
              aria-label={`집중 시간 ${formatMinutes(durationStepSec)} 늘리기`}
              disabled={isAdjustingDuration || state.session.plannedDurationSec >= 86400}
              onClick={() => void adjustDuration(1)}
            >
              <IconPlus aria-hidden="true" />
            </button>
          </div>
          <button className={styles.finish} type="button" disabled={isEnding} onClick={() => void finish()}>
            {isEnding ? <IconLoader2 className={styles.spinner} /> : <IconFlag />}
            {isEnding ? '기록 중…' : '세션 마치기'}
          </button>
        </section>
      )}

      {widgets.people && !focusMode && (
        <section className={`${styles.widget} ${styles.people}`} aria-label="함께하는 사람">
          <header><span>함께하는 사람</span><small>개인 세션</small></header>
          <div className={styles.avatar}>나</div>
          <p>현재 목표는 나에게만 표시됩니다.</p>
        </section>
      )}

      {widgets.music && !focusMode && (
        <SessionMusicPlayer
          key={state.session.musicUrl ?? 'no-music'}
          className={styles.music}
          source={state.session.musicUrl}
          options={musicOptions}
          onSourceChange={saveMusicSource}
        />
      )}

      <button
        type="button"
        className={styles['focus-status']}
        aria-pressed={focusMode}
        onClick={toggleFocusMode}
      >
        <IconBellOff aria-hidden="true" /> {focusMode ? '집중 모드 해제' : '집중 모드'}
      </button>
    </main>
  )
}
