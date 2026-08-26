import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { client } from './api/client'
import CreateProjectModal from './features/projects/CreateProjectModal'
import ProjectTagModal from './features/projects/ProjectTagModal'
import ProjectDashboard from './features/projects/ProjectDashboard'
import ProjectDetail from './features/projects/ProjectDetail'
import ProjectListPage from './features/projects/ProjectListPage'
import PersonalSessionPage from './features/sessions/PersonalSessionPage'
import DiveSessionFeedPage from './features/sessions/DiveSessionFeedPage'
import type { Project } from './features/projects/projectTypes'
import AppShell from './layout/AppShell'
import LoginPage from './pages/LoginPage'
import UserSettingsPage from './features/settings/UserSettingsPage'
import { authActions, useAuthStore } from './store/authStore'
import { useProjectStore } from './store/projectStore'
import styles from './App.module.css'

interface HealthResponse {
  status: 'UP'
  detail: { mysql: 'UP' }
}

type ResourceStatus = 'checking' | 'up' | 'unavailable'
type GuestView = 'home' | 'login'

function ProjectDetailRoute({ onDeleted }: { onDeleted: (projectId: number) => void }) {
  const { projectId } = useParams()
  const parsedProjectId = Number(projectId)
  const validProjectId = Number.isSafeInteger(parsedProjectId) && parsedProjectId > 0
    ? parsedProjectId
    : null

  return <ProjectDetail key={projectId ?? 'invalid'} projectId={validProjectId} onDeleted={onDeleted} />
}

function App() {
  const location = useLocation()
  const auth = useAuthStore()
  const [mysqlStatus, setMysqlStatus] = useState<ResourceStatus>('checking')
  const [guestView, setGuestView] = useState<GuestView>('home')
  const projects = useProjectStore((state) => state.projects)
  const projectStatus = useProjectStore((state) => state.status)
  const loadProjects = useProjectStore((state) => state.load)
  const addProject = useProjectStore((state) => state.add)
  const removeProject = useProjectStore((state) => state.remove)
  const resetProjects = useProjectStore((state) => state.reset)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [isTagModalOpen, setIsTagModalOpen] = useState(false)

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
    if (auth.status === 'unauthenticated') {
      resetProjects()
      return
    }

    const userId = auth.user?.id
    if (auth.status !== 'authenticated' || userId === undefined) return

    void loadProjects(userId)
  }, [auth.status, auth.user?.id, loadProjects, resetProjects])

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

  const retryLoadProjects = () => {
    const userId = auth.user?.id
    if (userId !== undefined) void loadProjects(userId)
  }

  const handleProjectCreated = (project: Project) => {
    addProject(project)
    setIsCreateModalOpen(false)
  }

  if (auth.status === 'authenticated' && location.pathname.startsWith('/sessions/')) {
    return (
      <Routes>
        <Route path="/sessions/:sessionId" element={<PersonalSessionPage />} />
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Routes>
    )
  }

  return (
    <AppShell
      userEmail={auth.user?.email ?? null}
      projectCount={auth.status === 'authenticated' ? projects.length : null}
      onLogin={() => setGuestView('login')}
    >
      {auth.status === 'unauthenticated' && guestView === 'login' ? (
        <LoginPage />
      ) : auth.status === 'authenticated' ? (
        <Routes>
          <Route path="/settings" element={<UserSettingsPage user={auth.user!} />} />
          <Route path="/sessions" element={<DiveSessionFeedPage />} />
          <Route
            path="/projects"
            element={(
              <>
                <ProjectListPage
                  projects={projects}
                  status={projectStatus}
                  onOpenCreate={() => setIsCreateModalOpen(true)}
                  onOpenTagManage={() => setIsTagModalOpen(true)}
                  onRetry={retryLoadProjects}
                />
                {isCreateModalOpen && (
                  <CreateProjectModal
                    onClose={() => setIsCreateModalOpen(false)}
                    onCreated={handleProjectCreated}
                  />
                )}
                {isTagModalOpen && <ProjectTagModal onClose={() => setIsTagModalOpen(false)} />}
              </>
            )}
          />
          <Route
            path="/pinboard"
            element={(
              <>
                <ProjectDashboard
                  projects={projects}
                  status={projectStatus}
                  onOpenCreate={() => setIsCreateModalOpen(true)}
                  onRetry={retryLoadProjects}
                />
                {isCreateModalOpen && (
                  <CreateProjectModal
                    onClose={() => setIsCreateModalOpen(false)}
                    onCreated={handleProjectCreated}
                  />
                )}
                {isTagModalOpen && <ProjectTagModal onClose={() => setIsTagModalOpen(false)} />}
              </>
            )}
          />
          <Route
            path="/projects/:projectId"
            element={<ProjectDetailRoute onDeleted={removeProject} />}
          />
          <Route path="*" element={<Navigate to="/projects" replace />} />
        </Routes>
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
