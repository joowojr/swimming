import { useEffect, useMemo, useState } from 'react'
import {
  IconArrowLeft,
  IconArrowsLeftRight,
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
import ModalTriggerButton from '../../components/ModalTriggerButton'
import { useAuthStore } from '../../store/authStore'
import { useFolderStore } from '../../store/folderStore.ts'
import { getPlaces } from '../places/placeApi'
import { getSession, updateSessionFocusDuration, updateSessionMusicUrl } from './sessionApi'
import type { SessionDetailResponse } from './sessionTypes'
import EndSessionModal from './EndSessionModal'
import SessionMusicPlayer from './music/SessionMusicPlayer'
import type { SessionMusicOption } from './music/SessionMusicPlayer'
import NoteCard from '../note/NoteCard'
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

function timerPhase(session: SessionDetailResponse) {
  const elapsed = Math.max(0, Math.floor((Date.now() - Date.parse(session.startedAt)) / 1000))
  const focus = session.focusDurationSec || session.plannedDurationSec
  const rest = session.breakDurationSec || 0
  const repeats = session.repeatCount || 1
  let cursor = elapsed
  for (let index = 0; index < repeats; index += 1) {
    if (cursor < focus) return { kind: 'focus' as const, remaining: focus - cursor, index: index + 1, repeats, elapsed }
    cursor -= focus
    if (index < repeats - 1 && rest > 0) {
      if (cursor < rest) return { kind: 'break' as const, remaining: rest - cursor, index: index + 1, repeats, elapsed }
      cursor -= rest
    }
  }
  return { kind: 'complete' as const, remaining: 0, index: repeats, repeats, elapsed }
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
  const auth = useAuthStore()
  const folders = useFolderStore((state) => state.folders)
  const { sessionId } = useParams()
  const parsedSessionId = Number(sessionId)
  const validSessionId = Number.isSafeInteger(parsedSessionId) && parsedSessionId > 0
    ? parsedSessionId
    : null
  const [state, setState] = useState<PageState>({ status: 'loading' })
  const [nowKey, setNowKey] = useState(0)
  const [isEndModalOpen, setIsEndModalOpen] = useState(false)
  const [isAdjustingDuration, setIsAdjustingDuration] = useState(false)
  const [durationStepSec, setDurationStepSec] = useState(10)
  const [requestKey, setRequestKey] = useState(0)
  const [widgets, setWidgets] = useState<WidgetVisibility>(INITIAL_WIDGET_VISIBILITY)
  const [isLayoutSwapped, setIsLayoutSwapped] = useState(false)
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
    void getPlaces()
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
  const phase = useMemo(
    () => session ? timerPhase(session) : { kind: 'complete' as const, remaining: 0, index: 1, repeats: 1, elapsed: 0 },
    // nowKey intentionally triggers calculation from the absolute start time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [session, nowKey],
  )
  const remaining = phase.remaining
  const maxFocusDurationSec = session
    ? Math.floor((86400 - session.breakDurationSec * Math.max(0, session.repeatCount - 1)) / session.repeatCount)
    : 86400
  const progress = session
    ? Math.min(1, Math.max(0, phase.elapsed / session.plannedDurationSec))
    : 0
  const ringOffset = 276.46 * (1 - progress)
  const backgroundAsset = session?.place.backgroundAsset
  const configuredBackgroundUrl = backgroundAsset?.url?.trim() || null
  const backgroundUrl = configuredBackgroundUrl && !hasBackgroundError
    ? configuredBackgroundUrl
    : DEFAULT_SESSION_BACKGROUND_URL
  const backgroundType = configuredBackgroundUrl && !hasBackgroundError
    ? backgroundAsset?.type
    : 'VIDEO'

  const handleBackgroundError = () => setHasBackgroundError(true)

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

  const openEndModal = () => {
    if (state.status !== 'ready') return
    setIsEndModalOpen(true)
  }

  const handleEnded = () => {
    setIsEndModalOpen(false)
    if (session) setState({ status: 'ended', session })
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
    const { breakDurationSec, repeatCount } = state.session
    const maxFocusDurationSec = Math.floor(
      (86400 - breakDurationSec * Math.max(0, repeatCount - 1)) / repeatCount,
    )
    const focusDurationSec = Math.min(
      maxFocusDurationSec,
      Math.max(60, state.session.focusDurationSec + direction * durationStepSec),
    )
    if (focusDurationSec === state.session.focusDurationSec) return
    const plannedDurationSec = focusDurationSec * repeatCount
      + breakDurationSec * Math.max(0, repeatCount - 1)

    setIsAdjustingDuration(true)
    try {
      await updateSessionFocusDuration(state.session.id, { focusDurationSec })
      setState((current) => current.status === 'ready'
        ? { ...current, session: { ...current.session, focusDurationSec, plannedDurationSec } }
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
          <button type="button" onClick={() => navigate('/folders')}>내 폴더로</button>
        </div>
      </main>
    )
  }

  if (state.status === 'ended') {
    return (
      <main className={`${styles.page} ${styles['is-ended']}`}>
        <div className={styles.scene} aria-hidden="true"><span /><span /><span /></div>
        {backgroundType === 'IMAGE' ? (
          <img
            className={styles['background-asset']}
            src={backgroundUrl}
            alt=""
            onError={handleBackgroundError}
          />
        ) : (
          <video
            className={styles['background-asset']}
            src={backgroundUrl}
            aria-hidden="true"
            autoPlay
            muted
            loop
            playsInline
            onError={handleBackgroundError}
          />
        )}
        <div className={styles['background-shade']} aria-hidden="true" />
        <section className={styles['ended-card']} aria-labelledby="session-ended-title">
          <span className={styles['ended-icon']}><IconCheck aria-hidden="true" /></span>
          <p>기록하기</p>
          <h2 id="session-ended-title">오늘의 집중을 잘 마무리했어요.</h2>
          <p>세션 기록을 저장했습니다.</p>
          <button type="button" onClick={() => navigate(-1)}>이전 페이지로 돌아가기</button>
        </section>
      </main>
    )
  }

  return (
    <main className={`${styles.page} ${focusMode ? styles['is-focus-mode'] : ''} ${isLayoutSwapped ? styles['is-layout-swapped'] : ''}`} aria-label="개인 세션 진행">
      <div className={styles.scene} aria-hidden="true"><span /><span /><span /></div>
      {backgroundType === 'IMAGE' && (
        <img
          className={styles['background-asset']}
          src={backgroundUrl}
          alt=""
          onError={handleBackgroundError}
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
          onError={handleBackgroundError}
        />
      )}
      <div className={styles['background-shade']} aria-hidden="true" />

      <div className={styles['session-chrome']}>
        <nav className={styles.dock} aria-label="세션 위젯">
          <button type="button" aria-pressed={widgets.place && !focusMode} aria-label={widgets.place ? '공간 위젯 접기' : '공간 위젯 펼치기'} onClick={() => toggleWidget('place')}><IconBuilding /></button>
          <button type="button" aria-pressed={widgets.tasks && !focusMode} aria-label={widgets.tasks ? '할 일 위젯 접기' : '할 일 위젯 펼치기'} onClick={() => toggleWidget('tasks')}><IconChecklist /></button>
          <button type="button" aria-pressed={widgets.timer} aria-label={widgets.timer ? '타이머 위젯 접기' : '타이머 위젯 펼치기'} onClick={() => toggleWidget('timer')}><IconClock /></button>
          <button type="button" aria-pressed={widgets.people && !focusMode} aria-label={widgets.people ? '참여자 위젯 접기' : '참여자 위젯 펼치기'} onClick={() => toggleWidget('people')}><IconUsers /></button>
          <button type="button" aria-pressed={widgets.music && !focusMode} aria-label={widgets.music ? '음악 위젯 접기' : '음악 위젯 펼치기'} onClick={() => toggleWidget('music')}><IconMusic /></button>
          <button type="button" aria-pressed={isLayoutSwapped} aria-label="위젯 좌우 위치 바꾸기" onClick={() => setIsLayoutSwapped((current) => !current)}><IconArrowsLeftRight /></button>
          <button type="button" aria-pressed={focusMode} aria-label={focusMode ? '집중 모드 해제' : '집중 모드 켜기'} onClick={toggleFocusMode}><IconBellOff /></button>
        </nav>

        <button className={`${styles.widget} ${styles.exit}`} type="button" onClick={() => navigate(-1)}>
          <IconArrowLeft aria-hidden="true" /> 나가기
        </button>
      </div>

      <div className={styles['widget-area']}>
      {widgets.place && !focusMode && (
        <div className={`${styles.widget} ${styles.place}`}>
          <IconBuilding aria-hidden="true" />
          <div><strong>{state.session.place.cityName}</strong><span>{state.session.place.name}</span></div>
        </div>
      )}

      {widgets.tasks && !focusMode && (
        <section className={`${styles.widget} ${styles.tasks}`} aria-labelledby="current-task-title">
          <p>지금 하는 일</p>
          {(session?.tasks ?? []).map((task, index) => (
            <div className={styles['current-task']} key={task.id}>
              <span aria-hidden="true" />
              <div>
                <h1 id={index === 0 ? 'current-task-title' : undefined}>{task.title}</h1>
                <p>{task.folderName}</p>
              </div>
            </div>
          ))}
        </section>
      )}

      {widgets.tasks && !focusMode && (
        <NoteCard
          className={`${styles.widget} ${styles['session-note']}`}
          folders={folders}
          sessionId={state.session.id}
        />
      )}

      {widgets.timer && (
        <section className={`${styles.widget} ${styles.timer}`} aria-labelledby="session-timer-title">
          <header><span>개인 집중</span><strong>{phase.kind === 'complete' ? '시간 완료' : phase.kind === 'break' ? '휴식' : '집중'}</strong></header>
          <div className={styles['timer-main']}>
            <svg viewBox="0 0 100 100" aria-hidden="true">
              <circle className={styles['ring-track']} cx="50" cy="50" r="44" />
              <circle className={styles['ring-progress']} cx="50" cy="50" r="44" style={{ strokeDashoffset: ringOffset }} />
            </svg>
            <div className={styles['timer-value']}>
              <h2 id="session-timer-title">{formatTimer(remaining)}</h2>
              <p>{phase.kind === 'complete' ? '세션 종료' : `${phase.index}/${phase.repeats}회 · 총 ${formatMinutes(state.session.plannedDurationSec)}`}</p>
            </div>
          </div>
          <div className={styles['timer-adjust']} aria-label="집중 시간 조절">
            <button
              type="button"
              aria-label={`집중 시간 ${formatMinutes(durationStepSec)} 줄이기`}
              disabled={isAdjustingDuration || state.session.focusDurationSec <= 60}
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
              disabled={isAdjustingDuration || state.session.focusDurationSec >= maxFocusDurationSec}
              onClick={() => void adjustDuration(1)}
            >
              <IconPlus aria-hidden="true" />
            </button>
          </div>
          <ModalTriggerButton
            className={styles.finish}
            dialogId="end-session-dialog"
            icon={<IconFlag aria-hidden="true" />}
            isOpen={isEndModalOpen}
            onClick={openEndModal}
          >
            세션 마치기
          </ModalTriggerButton>
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
          historyOwnerId={auth.user?.id ?? null}
          onSourceChange={saveMusicSource}
        />
      )}
      </div>

      <button
        type="button"
        className={styles['focus-status']}
        aria-pressed={focusMode}
        onClick={toggleFocusMode}
      >
        <IconBellOff aria-hidden="true" /> {focusMode ? '집중 모드 해제' : '집중 모드'}
      </button>

      {isEndModalOpen && state.status === 'ready' && (
        <EndSessionModal
          sessionId={state.session.id}
          tasks={state.session.tasks}
          onClose={() => setIsEndModalOpen(false)}
          onEnded={handleEnded}
        />
      )}
    </main>
  )
}
