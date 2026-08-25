import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  IconCheck,
  IconClock,
  IconListCheck,
  IconMapPin,
  IconPlayerPause,
  IconPlayerPlay,
} from '@tabler/icons-react'
import { getSessions } from './sessionApi'
import type { SessionDetailResponse, SessionStatus } from './sessionTypes'
import styles from './DiveSessionFeedPage.module.css'

const statusLabels: Record<SessionStatus, string> = {
  IN_PROGRESS: '진행 중',
  COMPLETED: '완료',
  INTERRUPTED: '중단',
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value))
}

function formatDuration(seconds: number | null) {
  if (seconds === null) return '기록 없음'
  const minutes = Math.max(1, Math.round(seconds / 60))
  return `${minutes}분 집중`
}

function StatusIcon({ status }: { status: SessionStatus }) {
  if (status === 'COMPLETED') return <IconCheck size={18} aria-hidden="true" />
  if (status === 'INTERRUPTED') return <IconPlayerPause size={18} aria-hidden="true" />
  return <IconPlayerPlay size={18} aria-hidden="true" />
}

export default function DiveSessionFeedPage() {
  const [sessions, setSessions] = useState<SessionDetailResponse[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')

  useEffect(() => {
    let active = true
    void getSessions()
      .then((response) => {
        if (!active) return
        setSessions(response)
        setStatus('ready')
      })
      .catch(() => {
        if (active) setStatus('error')
      })

    return () => { active = false }
  }, [])

  return (
    <main className={styles.page}>
      <section className={styles['recent-section']} aria-labelledby="recent-sessions-title">
        <header className={styles.header}>
          <h1 id="recent-sessions-title">최근 진행한 다이브 세션</h1>
          <p>집중한 시간과 할 일을 다시 살펴봅니다.</p>
        </header>

        {status === 'loading' && (
          <div className={styles.grid} aria-label="세션 목록 불러오는 중">
            {[1, 2, 3, 4].map((item) => <div className={styles.skeleton} key={item} />)}
          </div>
        )}

        {status === 'error' && (
          <section className={styles.state} role="alert">
            <IconPlayerPause size={24} aria-hidden="true" />
            <strong>세션을 불러오지 못했습니다.</strong>
            <span>잠시 후 다시 시도해 주세요.</span>
          </section>
        )}

        {status === 'ready' && sessions.length === 0 && (
          <section className={styles.state}>
            <IconClock size={24} aria-hidden="true" />
            <strong>아직 기록된 다이브 세션이 없습니다.</strong>
            <span>오늘의 Task에서 첫 세션을 시작해 보세요.</span>
            <Link className={styles['state-link']} to="/projects">프로젝트 보기</Link>
          </section>
        )}

        {status === 'ready' && sessions.length > 0 && (
          <div className={styles.grid}>
            {sessions.map((session) => (
            <Link className={styles.card} key={session.id} to={`/sessions/${session.id}`}>
              <div className={`${styles.thumbnail} ${styles[`thumbnail-${session.status.toLowerCase()}`]}`}>
                <StatusIcon status={session.status} />
                <span>{statusLabels[session.status]}</span>
                <strong>{formatDuration(session.actualDurationSec ?? session.plannedDurationSec)}</strong>
              </div>
              <div className={styles['card-content']}>
                <div className={styles['card-title-row']}>
                  <h2>{session.tasks[0]?.title ?? '할 일 없는 세션'}</h2>
                  <span className={styles['task-count']}>
                    <IconListCheck size={15} aria-hidden="true" />
                    {session.tasks.length}
                  </span>
                </div>
                <div className={styles.meta}>
                  <span><IconMapPin size={15} aria-hidden="true" />{session.place.cityName} · {session.place.name}</span>
                  <span><IconClock size={15} aria-hidden="true" />{formatDateTime(session.startedAt)}</span>
                </div>
              </div>
            </Link>
            ))}
          </div>
        )}
      </section>

      <section className={styles['group-section']} aria-labelledby="group-sessions-title">
        <div className={styles['section-heading']}>
          <div>
            <p className={styles.eyebrow}>함께 집중하기</p>
            <h2 id="group-sessions-title">그룹 세션</h2>
          </div>
          <span className={styles['coming-soon']}>준비중</span>
        </div>
        <div className={styles['group-placeholder']}>
          <span>그룹 세션을 준비하고 있어요.</span>
        </div>
      </section>
    </main>
  )
}
