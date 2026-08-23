import { useEffect, useState } from 'react'
import { IconArrowRight, IconClock, IconPlayerPlay } from '@tabler/icons-react'
import { Link } from 'react-router-dom'
import { getActiveSession } from './sessionApi'
import type { ActiveSessionResponse } from './sessionTypes'
import styles from './ContinueSessionWidget.module.css'

const DEFAULT_THUMBNAIL_URL = '/lisbon_1.mp4'

type WidgetState =
    | { status: 'loading' }
    | { status: 'ready'; session: ActiveSessionResponse | null }
    | { status: 'error' }

function formatDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  return minutes >= 60 && minutes % 60 === 0 ? `${minutes / 60}시간` : `${minutes}분`
}

export default function ContinueSessionWidget() {
  const [state, setState] = useState<WidgetState>({ status: 'loading' })

  useEffect(() => {
    let active = true
    void getActiveSession()
        .then((response) => {
          if (active) setState({ status: 'ready', session: response })
        })
        .catch(() => {
          if (active) setState({ status: 'error' })
        })
    return () => { active = false }
  }, [])

  const session = state.status === 'ready' ? state.session : null
  const isEmpty = state.status === 'ready' && !session

  const backgroundAsset = session?.place.backgroundAsset
  const thumbnailUrl = backgroundAsset?.url?.trim() || DEFAULT_THUMBNAIL_URL
  const isVideo = !backgroundAsset?.url?.trim() || backgroundAsset.type === 'VIDEO'
  const currentTask = session?.tasks[0]

  return (
      <section
          className={`${styles.widget} ${isEmpty ? styles['is-invite'] : ''}`}
          aria-labelledby="continue-session-title"
      >
        <div className={styles.thumbnail} aria-hidden="true">
          {isVideo ? (
              <video src={thumbnailUrl} autoPlay muted loop playsInline preload="metadata" />
          ) : (
              <img src={thumbnailUrl} alt="" />
          )}
        </div>

        <div className={styles.content}>
          <header>
            <h2 id="continue-session-title">
              {isEmpty ? '잠시 떠나볼까요' : '이어서 하기'}
            </h2>
            <span>{session ? '진행 중인 세션' : '오늘의 집중'}</span>
          </header>

          {state.status === 'loading' ? (
              <p className={styles.status} role="status">세션을 확인하고 있습니다.</p>
          ) : session ? (
              <>
                <strong className={styles.task}>{currentTask?.title ?? '개인 집중 세션'}</strong>
                <p>{currentTask?.projectName ?? session.place.name}</p>
                <div className={styles.meta}>
                  <span>{session.place.cityName} · {session.place.name}</span>
                  <span><IconClock aria-hidden="true" />{formatDuration(session.plannedDurationSec)}</span>
                </div>
              </>
          ) : state.status === 'error' ? (
              <p className={styles.status}>세션을 확인하지 못했습니다.</p>
          ) : (
              <>
                <strong className={styles.task}>Lisbon · Alfama Cafe</strong>
                <p className={styles['invite-copy']}>
                  45분만 다른 도시에서 집중해보세요.
                </p>
                <Link
                    className={`${styles.action} ${styles['action-primary']}`}
                    to="/sessions/new"
                >
                  <IconPlayerPlay aria-hidden="true" />
                  <span>세션 시작하기</span>
                </Link>
              </>
          )}
        </div>

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