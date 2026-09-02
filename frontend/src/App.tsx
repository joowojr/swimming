import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { client } from './api/client'
import CreateFolderModal from './features/folders/CreateFolderModal.tsx'
import FolderTagModal from './features/folders/FolderTagModal.tsx'
import PinBoard from './features/folders/PinBoard.tsx'
import FolderDetail from './features/folders/FolderDetail.tsx'
import FolderListPage from './features/folders/FolderListPage.tsx'
import PersonalSessionPage from './features/sessions/PersonalSessionPage'
import DiveSessionFeedPage from './features/sessions/DiveSessionFeedPage'
import type { Folder } from './features/folders/folderTypes.ts'
import AppShell from './layout/AppShell'
import LoginPage from './pages/LoginPage'
import UserSettingsPage from './features/settings/UserSettingsPage'
import { authActions, useAuthStore } from './store/authStore'
import { useFolderStore } from './store/folderStore.ts'
import styles from './App.module.css'

interface HealthResponse {
  status: 'UP'
  detail: { postgres: 'UP' }
}

type ResourceStatus = 'checking' | 'up' | 'unavailable'
type GuestView = 'home' | 'login'

function ProjectDetailRoute({ onDeleted }: { onDeleted: (folderId: number) => void }) {
  const { folderId: folderId } = useParams()
  const parsedProjectId = Number(folderId)
  const validProjectId = Number.isSafeInteger(parsedProjectId) && parsedProjectId > 0
    ? parsedProjectId
    : null

  return <FolderDetail key={folderId ?? 'invalid'} folderId={validProjectId} onDeleted={onDeleted} />
}

function App() {
  const location = useLocation()
  const auth = useAuthStore()
  const [postgresStatus, setPostgresStatus] = useState<ResourceStatus>('checking')
  const [guestView, setGuestView] = useState<GuestView>('home')
  const folders = useFolderStore((state) => state.folders)
  const folderStatus = useFolderStore((state) => state.status)
  const loadFolders = useFolderStore((state) => state.load)
  const addFolder = useFolderStore((state) => state.add)
  const removeFolder = useFolderStore((state) => state.remove)
  const resetFolders = useFolderStore((state) => state.reset)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [isTagModalOpen, setIsTagModalOpen] = useState(false)

  useEffect(() => { void authActions.initialize() }, [])

  useEffect(() => {
    if (auth.status === 'checking') return

    const checkHealth = async () => {
      try {
        const response = await client.get<HealthResponse>('/health')
        setPostgresStatus(
          response.data.status === 'UP' && response.data.detail.postgres === 'UP'
            ? 'up'
            : 'unavailable',
        )
      } catch {
        setPostgresStatus('unavailable')
      }
    }

    void checkHealth()
  }, [auth.status])

  useEffect(() => {
    if (auth.status === 'unauthenticated') {
      resetFolders()
      return
    }

    const userId = auth.user?.id
    if (auth.status !== 'authenticated' || userId === undefined) return

    void loadFolders(userId)
  }, [auth.status, auth.user?.id, loadFolders, resetFolders])

  if (auth.status === 'checking') {
    return (
      <main className={styles['auth-loading']}>
        <p className={styles['route-label']} role="status">로그인 상태 확인 중…</p>
      </main>
    )
  }

  const postgresStatusMessage = {
    checking: '연결 확인 중',
    up: '연결됨',
    unavailable: '연결 대기 중',
  }[postgresStatus]

  const retryLoadProjects = () => {
    const userId = auth.user?.id
    if (userId !== undefined) void loadFolders(userId)
  }

  const handleProjectCreated = (folder: Folder) => {
    addFolder(folder)
    setIsCreateModalOpen(false)
  }

  const handleLogout = async () => {
    await authActions.logout()
    setGuestView('login')
  }

  if (auth.status === 'authenticated' && location.pathname.startsWith('/sessions/')) {
    return (
      <Routes>
        <Route path="/sessions/:sessionId" element={<PersonalSessionPage />} />
        <Route path="*" element={<Navigate to="/folders" replace />} />
      </Routes>
    )
  }

  return (
    <AppShell
      userEmail={auth.user?.email ?? null}
      folderCount={auth.status === 'authenticated' ? folders.length : null}
      onLogin={() => setGuestView('login')}
    >
      {auth.status === 'unauthenticated' && guestView === 'login' ? (
        <LoginPage />
      ) : auth.status === 'authenticated' ? (
        <Routes>
          <Route path="/settings" element={<UserSettingsPage user={auth.user!} onLogout={handleLogout} />} />
          <Route path="/sessions" element={<DiveSessionFeedPage />} />
          <Route
            path="/folders"
            element={(
              <>
                <FolderListPage
                  folders={folders}
                  status={folderStatus}
                  onOpenCreate={() => setIsCreateModalOpen(true)}
                  onOpenTagManage={() => setIsTagModalOpen(true)}
                  onRetry={retryLoadProjects}
                />
                {isCreateModalOpen && (
                  <CreateFolderModal
                    onClose={() => setIsCreateModalOpen(false)}
                    onCreated={handleProjectCreated}
                  />
                )}
                {isTagModalOpen && (
                  <FolderTagModal
                    onClose={() => setIsTagModalOpen(false)}
                    onChanged={retryLoadProjects}
                  />
                )}
              </>
            )}
          />
          <Route
            path="/pinboard"
            element={(
              <>
                <PinBoard
                  folders={folders}
                  status={folderStatus}
                  onRetry={retryLoadProjects}
                />
                {isTagModalOpen && (
                  <FolderTagModal
                    onClose={() => setIsTagModalOpen(false)}
                    onChanged={retryLoadProjects}
                  />
                )}
              </>
            )}
          />
          <Route
            path="/folders/:folderId"
            element={<ProjectDetailRoute onDeleted={removeFolder} />}
          />
          <Route path="*" element={<Navigate to="/folders" replace />} />
        </Routes>
      ) : (
        <section className={styles['home-overview']} aria-live="polite">
          <p className={styles.eyebrow}>Swimming workspace</p>
          <h1>내 폴더</h1>
          <p className={styles.description}>
            폴더와 몰입 세션을 한 단계씩 쌓아갈 기본 환경입니다.
          </p>
          <div className={styles['connection-status']}>
            <span className={styles['status-dot']} aria-hidden="true" />
            <span>PostgreSQL</span>
            <strong>{postgresStatusMessage}</strong>
          </div>
        </section>
      )}
    </AppShell>
  )
}

export default App
