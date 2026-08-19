import { useEffect, useState } from 'react'
import { client } from './api/client'
import LoginPage from './pages/LoginPage'
import { authActions, useAuthStore } from './store/authStore'
import './App.css'

interface HealthResponse {
  status: 'UP'
  detail: {
    mysql: 'UP'
  }
}

type ResourceStatus = 'checking' | 'up' | 'unavailable'

function App() {
  const auth = useAuthStore()
  const [mysqlStatus, setMysqlStatus] = useState<ResourceStatus>('checking')

  useEffect(() => {
    void authActions.initialize()
  }, [])

  useEffect(() => {
    if (auth.status !== 'authenticated') {
      return
    }

    const checkHealth = async () => {
      try {
        const response = await client.get<HealthResponse>('/health')
        setMysqlStatus(
          response.data.status === 'UP' && response.data.detail.mysql === 'UP'
            ? 'up'
            : 'unavailable',
        )
      } catch {
        setMysqlStatus('unavailable')
      }
    }

    void checkHealth()
  }, [auth.status])

  if (auth.status === 'checking') {
    return (
      <main className="auth-loading">
        <p className="route-label" role="status">
          로그인 상태 확인 중…
        </p>
      </main>
    )
  }

  if (auth.status === 'unauthenticated') {
    return <LoginPage />
  }

  const mysqlStatusMessage = {
    checking: '연결 확인 중',
    up: '연결됨',
    unavailable: '연결 대기 중',
  }[mysqlStatus]

  return (
    <main className="setup-page">
      <section className="setup-card" aria-live="polite">
        <p className="eyebrow">Swimming workspace</p>
        <h1>프로젝트 스켈레톤</h1>
        <p className="account-email">{auth.user?.email}</p>
        <p className="description">
          프로젝트와 몰입 세션을 한 단계씩 쌓아갈 기본 환경입니다.
        </p>
        <div className="connection-status">
          <span className="status-dot" aria-hidden="true" />
          <span>MySQL</span>
          <strong>{mysqlStatusMessage}</strong>
        </div>
        <button
          className="logout-button"
          type="button"
          onClick={() => void authActions.logout()}
        >
          로그아웃
        </button>
      </section>
    </main>
  )
}

export default App
