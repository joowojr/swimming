import { useEffect, useState } from 'react'
import { client } from './api/client'
import CreateProjectModal from './features/projects/CreateProjectModal'
import ProjectDashboard from './features/projects/ProjectDashboard'
import type { ProjectLoadStatus } from './features/projects/ProjectDashboard'
import { getProjects } from './features/projects/projectApi'
import type { Project } from './features/projects/projectTypes'
import AppShell from './layout/AppShell'
import LoginPage from './pages/LoginPage'
import { authActions, useAuthStore } from './store/authStore'
import styles from './App.module.css'

interface HealthResponse {
  status: 'UP'
  detail: { mysql: 'UP' }
}

type ResourceStatus = 'checking' | 'up' | 'unavailable'
type GuestView = 'home' | 'login'

function App() {
  const auth = useAuthStore()
  const [mysqlStatus, setMysqlStatus] = useState<ResourceStatus>('checking')
  const [guestView, setGuestView] = useState<GuestView>('home')
  const [projects, setProjects] = useState<Project[]>([])
  const [projectsOwnerId, setProjectsOwnerId] = useState<number | null>(null)
  const [projectStatus, setProjectStatus] = useState<ProjectLoadStatus>('idle')
  const [projectRequestKey, setProjectRequestKey] = useState(0)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)

  useEffect(() => { void authActions.initialize() }, [])

  useEffect(() => {
    if (auth.status === 'checking') return

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

  useEffect(() => {
    const userId = auth.user?.id
    if (auth.status !== 'authenticated' || userId === undefined) return

    let active = true

    void getProjects()
      .then((response) => {
        if (!active) return
        setProjects(response)
        setProjectsOwnerId(userId)
        setProjectStatus('ready')
      })
      .catch(() => {
        if (!active) return
        setProjectsOwnerId(userId)
        setProjectStatus('error')
      })

    return () => { active = false }
  }, [auth.status, auth.user?.id, projectRequestKey])

  if (auth.status === 'checking') {
    return (
      <main className={styles['auth-loading']}>
        <p className={styles['route-label']} role="status">로그인 상태 확인 중…</p>
      </main>
    )
  }

  const mysqlStatusMessage = {
    checking: '연결 확인 중',
    up: '연결됨',
    unavailable: '연결 대기 중',
  }[mysqlStatus]

  const visibleProjects = auth.user?.id === projectsOwnerId ? projects : []
  const visibleProjectStatus = auth.user?.id === projectsOwnerId ? projectStatus : 'idle'

  return (
    <AppShell
      userEmail={auth.user?.email ?? null}
      projectCount={auth.status === 'authenticated' ? visibleProjects.length : null}
      onLogin={() => setGuestView('login')}
    >
      {auth.status === 'unauthenticated' && guestView === 'login' ? (
        <LoginPage />
      ) : auth.status === 'authenticated' ? (
        <>
          <ProjectDashboard
            projects={visibleProjects}
            status={visibleProjectStatus}
            onOpenCreate={() => setIsCreateModalOpen(true)}
            onRetry={() => {
              setProjectsOwnerId(auth.user?.id ?? null)
              setProjectStatus('loading')
              setProjectRequestKey((key) => key + 1)
            }}
          />
          {isCreateModalOpen && (
            <CreateProjectModal
              onClose={() => setIsCreateModalOpen(false)}
              onCreated={(project) => {
                setProjects((currentProjects) => [project, ...currentProjects])
                setProjectsOwnerId(auth.user?.id ?? null)
                setProjectStatus('ready')
                setIsCreateModalOpen(false)
              }}
            />
          )}
        </>
      ) : (
        <section className={styles['home-overview']} aria-live="polite">
          <p className={styles.eyebrow}>Swimming workspace</p>
          <h1>내 프로젝트</h1>
          <p className={styles.description}>
            프로젝트와 몰입 세션을 한 단계씩 쌓아갈 기본 환경입니다.
          </p>
          <div className={styles['connection-status']}>
            <span className={styles['status-dot']} aria-hidden="true" />
            <span>MySQL</span>
            <strong>{mysqlStatusMessage}</strong>
          </div>
        </section>
      )}
    </AppShell>
  )
}

export default App
