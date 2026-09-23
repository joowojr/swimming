import { useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import CreateFolderModal from './features/folders/CreateFolderModal.tsx'
import FolderTagModal from './features/folders/FolderTagModal.tsx'
import PinBoard from './features/folders/PinBoard.tsx'
import FolderDetail from './features/folders/FolderDetail.tsx'
import FolderListPage from './features/folders/FolderListPage.tsx'
import PersonalSessionPage from './features/sessions/PersonalSessionPage'
import TasksPage from './features/tasks/TasksPage'
import CoworkBoardPage from './features/agent-work/CoworkBoardPage'
import DiveSessionFeedPage from './features/sessions/DiveSessionFeedPage'
import type { Folder } from './features/folders/folderTypes.ts'
import AppShell from './layout/AppShell'
import TimerEndSoundScheduler from './features/timer/TimerEndSoundScheduler'
import LoginPage from './pages/login/LoginPage.tsx'
import PublicHomePage from './pages/landing/PublicHomePage.tsx'
import UserSettingsPage from './features/settings/UserSettingsPage'
import { authActions, useAuthStore } from './store/authStore'
import { useFolderStore } from './store/folderStore.ts'
import { useActiveSessionStore } from './store/activeSessionStore'
import { useDailyPlanStore } from './store/dailyPlanStore'
import { useSourceStore } from './store/sourceStore'
import { useTaskStore } from './store/taskStore'
import HealthPage from './features/health/HealthPage'
import styles from './App.module.css'


function ProjectDetailRoute({ onDeleted }: { onDeleted: (folderId: number) => void }) {
  const { folderId, '*': childPath } = useParams()
  const parsedProjectId = Number(folderId)
  const validProjectId = Number.isSafeInteger(parsedProjectId) && parsedProjectId > 0
    ? parsedProjectId
    : null

  if (childPath && childPath !== 'links') {
    return <Navigate to="/folders" replace />
  }

  return <FolderDetail key={folderId ?? 'invalid'} folderId={validProjectId} onDeleted={onDeleted} />
}

function App() {
  const location = useLocation()
  const navigate = useNavigate()
  const auth = useAuthStore()
  const folders = useFolderStore((state) => state.folders)
  const folderStatus = useFolderStore((state) => state.status)
  const loadFolders = useFolderStore((state) => state.load)
  const folderFilter = useFolderStore((state) => state.filter)
  const changeFolderFilter = useFolderStore((state) => state.changeFilter)
  const addFolder = useFolderStore((state) => state.add)
  const removeFolder = useFolderStore((state) => state.remove)
  const resetFolders = useFolderStore((state) => state.reset)
  const clearActiveSession = useActiveSessionStore((state) => state.clear)
  const activeSessionStatus = useActiveSessionStore((state) => state.status)
  const loadActiveSession = useActiveSessionStore((state) => state.load)
  const resetTasks = useTaskStore((state) => state.reset)
  const resetSources = useSourceStore((state) => state.reset)
  const resetDailyPlans = useDailyPlanStore((state) => state.reset)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [isTagModalOpen, setIsTagModalOpen] = useState(false)

  useEffect(() => { void authActions.initialize() }, [])

  useEffect(() => {
    if (auth.status === 'unauthenticated') {
      resetFolders()
      clearActiveSession()
      resetTasks()
      resetSources()
      resetDailyPlans()
      return
    }

    const userId = auth.user?.id
    if (auth.status !== 'authenticated' || userId === undefined) return

    void loadFolders(userId)
  }, [auth.status, auth.user?.id, clearActiveSession, loadFolders, resetDailyPlans, resetFolders, resetSources, resetTasks])

  // 진행 중인 세션은 로그인한 뒤 어느 화면으로 들어오든 한 번 불러온다. 이후 갱신은 세션을 다루는 화면이 맡는다.
  // 세션 화면은 AppShell 밖이라 셸 안의 위젯에 맡기면 세션 화면으로 바로 들어올 때 비어 있다.
  useEffect(() => {
    if (auth.status === 'authenticated' && activeSessionStatus === 'idle') void loadActiveSession()
  }, [auth.status, activeSessionStatus, loadActiveSession])

  if (auth.status === 'checking') {
    return (
      <main className={styles['auth-loading']}>
        <p className={styles['route-label']} role="status">로그인 상태 확인 중…</p>
      </main>
    )
  }


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
    navigate('/')
  }

  // 알림음 예약은 로그인한 뒤의 두 분기(세션 화면, 셸)에 모두 둔다. 오가며 다시 붙어도 지난 시각은 걸지 않는다.
  if (auth.status === 'authenticated' && location.pathname.startsWith('/sessions/')) {
    return (
      <>
        <TimerEndSoundScheduler />
        <Routes>
          <Route path="/sessions/:sessionId" element={<PersonalSessionPage />} />
          <Route path="*" element={<Navigate to="/pinboard" replace />} />
        </Routes>
      </>
    )
  }

  if (auth.status === 'unauthenticated') {
    return (
      <Routes>
        <Route path="/health" element={<HealthPage />} />
        <Route path="/" element={<PublicHomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<PublicHomePage />} />
      </Routes>
    )
  }

  return (
    <AppShell
      userEmail={auth.user?.email ?? null}
      onLogin={() => navigate('/')}
      chromeTone={location.pathname === '/agent-board' ? 'page' : 'default'}
    >
      <TimerEndSoundScheduler />
      <Routes>
        <Route path="/settings" element={<UserSettingsPage user={auth.user!} onLogout={handleLogout} />} />
        <Route path="/sessions" element={<DiveSessionFeedPage />} />
        <Route path="/tasks" element={<TasksPage folders={folders} />} />
        <Route path="/agent-board" element={<CoworkBoardPage />} />
        <Route
          path="/folders"
          element={(
            <>
              <FolderListPage
                folders={folders}
                status={folderStatus}
                filter={folderFilter}
                onFilterChange={(filter) => void changeFolderFilter(filter)}
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
          path="/folders/:folderId/*"
          element={<ProjectDetailRoute onDeleted={removeFolder} />}
        />
        <Route path="/health" element={<HealthPage />} />
        <Route path="*" element={<Navigate to="/pinboard" replace />} />
      </Routes>
    </AppShell>
  )
}

export default App
