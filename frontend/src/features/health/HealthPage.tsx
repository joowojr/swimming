import { useEffect, useState } from 'react'
import { client } from '../../api/client'
import styles from './HealthPage.module.css'

interface HealthResponse {
  status: 'UP'
  detail: { postgres: 'UP' }
}

type ResourceStatus = 'checking' | 'up' | 'unavailable'

const STATUS_MESSAGE: Record<ResourceStatus, string> = {
  checking: '확인 중',
  up: '연결됨',
  unavailable: '연결 안 됨',
}

/** 역할: 서버와 DB 연결 상태를 확인하는 별도 화면. 초기 화면이 아니라 /health 경로에서만 쓴다. */
export default function HealthPage() {
  const [postgresStatus, setPostgresStatus] = useState<ResourceStatus>('checking')

  useEffect(() => {
    let active = true
    void client.get<HealthResponse>('/health')
      .then((response) => {
        if (!active) return
        setPostgresStatus(
          response.data.status === 'UP' && response.data.detail.postgres === 'UP'
            ? 'up'
            : 'unavailable',
        )
      })
      .catch(() => {
        if (active) setPostgresStatus('unavailable')
      })
    return () => { active = false }
  }, [])

  return (
    <section className={styles['health-overview']} aria-live="polite">
      <p className={styles.eyebrow}>Swimming workspace</p>
      <h1>연결 상태</h1>
      <p className={styles.description}>
        서버와 데이터베이스 연결을 확인하는 화면입니다.
      </p>
      <div className={styles['connection-status']}>
        <span className={styles['status-dot']} aria-hidden="true" />
        <span>PostgreSQL</span>
        <strong>{STATUS_MESSAGE[postgresStatus]}</strong>
      </div>
    </section>
  )
}
