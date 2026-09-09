import { useEffect, useMemo, useState } from 'react'
import { IconArrowRight, IconClock, IconMapPin, IconPlayerPlay } from '@tabler/icons-react'
import { Link, useNavigate } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import type { DailyPlanItem } from '../plans/dailyPlanTypes'
import { getTodayPlanItems } from '../plans/todayPlan'
import CreateSessionModal from './CreateSessionModal'
import { useActiveSessionStore } from '../../store/activeSessionStore'
import { pickRandomPlace, usePlaceStore } from '../../store/placeStore'
import styles from './ContinueSessionWidget.module.css'

type ContinueSessionWidgetVariant = 'home' | 'empty-session'

interface ContinueSessionWidgetProps {
  variant?: ContinueSessionWidgetVariant
}

function formatDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  return minutes >= 60 && minutes % 60 === 0 ? `${minutes / 60}시간` : `${minutes}분`
}

export default function ContinueSessionWidget({ variant = 'home' }: ContinueSessionWidgetProps) {
  const navigate = useNavigate()
  const session = useActiveSessionStore((state) => state.session)
  const status = useActiveSessionStore((state) => state.status)
  const loadActiveSession = useActiveSessionStore((state) => state.load)
  const markSessionCreated = useActiveSessionStore((state) => state.markCreated)
  const cities = usePlaceStore((state) => state.cities)
  const loadPlaces = usePlaceStore((state) => state.load)
  // 마운트 시 한 번 정해 두고, 카탈로그가 늦게 도착해도 같은 배경을 유지한다.
  const [backgroundSeed] = useState(() => Math.random())
  const [todayTasks, setTodayTasks] = useState<DailyPlanItem[] | null>(null)
  const [isPreparingStart, setIsPreparingStart] = useState(false)
  const [startError, setStartError] = useState<string | null>(null)

  useEffect(() => {
    void loadActiveSession()
  }, [loadActiveSession])

  const isLoading = status === 'idle' || status === 'loading'
  const isEmpty = status === 'ready' && !session

  const sessionBackground = session?.place.backgroundAsset
  const hasSessionBackground = Boolean(sessionBackground?.url?.trim())

  // 진행 중인 세션의 배경이 없을 때만 카탈로그가 필요하다.
  useEffect(() => {
    if (status === 'ready' && !hasSessionBackground) void loadPlaces()
  }, [status, hasSessionBackground, loadPlaces])

  const openStartModal = async () => {
    setIsPreparingStart(true)
    setStartError(null)
    try {
      setTodayTasks(await getTodayPlanItems())
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
          ? (error as ApiError).message
          : undefined
      setStartError(apiMessage ?? '오늘 계획을 불러오지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsPreparingStart(false)
    }
  }

  const fallbackPlace = useMemo(
    () => pickRandomPlace(cities, backgroundSeed),
    [cities, backgroundSeed],
  )
  const background = hasSessionBackground
    ? sessionBackground
    : fallbackPlace?.place.backgroundAsset
  const currentTask = session?.tasks[0]

  return (
      <section
          className={`${styles.widget} ${styles[variant]} ${isEmpty ? styles['is-invite'] : ''}`}
          aria-labelledby="continue-session-title"
      >
        <div className={styles.thumbnail} aria-hidden="true">
          {background?.url && (background.type === 'VIDEO' ? (
              <video
                src={background.thumbnailUrl ?? background.url}
                autoPlay
                muted
                loop
                playsInline
                preload="metadata"
              />
          ) : (
              <img src={background.url} alt="" />
          ))}
        </div>

        <div className={styles.content}>
          <header>
            <h2 id="continue-session-title">
              {isEmpty
                ? variant === 'empty-session' ? '첫 다이브를 시작해볼까요' : '몰입을 시작해보세요'
                : '이어서 하기'}
            </h2>
            <span>{session ? '진행 중인 세션' : '오늘의 집중'}</span>
          </header>

          {isLoading ? (
              <p className={styles.status} role="status">세션을 확인하고 있습니다.</p>
          ) : session ? (
              <>
                <strong className={styles.task}>{currentTask?.title ?? '개인 집중 세션'}</strong>
                <p>{currentTask?.folderName ?? session.place.name}</p>
                <div className={styles.meta}>
                  <span>{session.place.cityName} · {session.place.name}</span>
                  <span><IconClock aria-hidden="true" />{formatDuration(session.plannedDurationSec)}</span>
                </div>
              </>
          ) : status === 'error' ? (
              <p className={styles.status}>세션을 확인하지 못했습니다.</p>
          ) : (
              <>
                {fallbackPlace ? (
                    <strong className={`${styles.task} ${styles['task-location']}`}>
                      <IconMapPin aria-hidden="true" />
                      <span>{fallbackPlace.cityName} · {fallbackPlace.place.name}</span>
                    </strong>
                ) : (
                    <strong className={styles.task}>
                      {variant === 'empty-session' ? '나만의 첫 집중 시간' : '오늘의 다이브 세션'}
                    </strong>
                )}
                <p className={styles['invite-copy']}>
                  {variant === 'empty-session'
                    ? '아직 진행한 세션이 없습니다. 첫 다이브 세션을 시작해 보세요.'
                    : '오늘 일정에서 할 일을 골라 다이브 세션을 시작해 보세요.'}
                </p>
                <ModalTriggerButton
                    className={`${styles.action} ${styles['action-primary']}`}
                    dialogId="create-session-dialog"
                    icon={<IconPlayerPlay aria-hidden="true" />}
                    isOpen={todayTasks !== null}
                    isPreparing={isPreparingStart}
                    preparingLabel="준비하는 중…"
                    variant="plain"
                    onClick={() => void openStartModal()}
                >
                  <span>다이브 세션 시작하기</span>
                </ModalTriggerButton>
                {startError && <p className={styles.status} role="alert">{startError}</p>}
              </>
          )}
        </div>

        {todayTasks !== null && (
            <CreateSessionModal
                todayTasks={todayTasks}
                onClose={() => setTodayTasks(null)}
                onStarted={(startedSession) => {
                  setTodayTasks(null)
                  markSessionCreated(startedSession)
                  navigate(`/sessions/${startedSession.id}`)
                }}
            />
        )}

        {session && (
            <Link
                className={styles.action}
                to={`/sessions/${session.id}`}
                aria-label={`${currentTask?.title ?? '개인 집중 세션'} 이어서 하기`}
            >
              <span>계속하기</span>
              <IconArrowRight aria-hidden="true" />
            </Link>
        )}
      </section>
  )
}
