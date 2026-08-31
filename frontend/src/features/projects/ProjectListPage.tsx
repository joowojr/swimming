import { useEffect, useMemo, useState } from 'react'
import { IconFilter, IconFolders, IconPlus, IconTags } from '@tabler/icons-react'
import { useSearchParams } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import DeleteIconButton from '../../components/DeleteIconButton'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import { deleteTasks, getTaskList } from '../tasks/taskApi'
import type { TaskListMode, TaskResponse } from '../tasks/taskTypes'
import ProjectCard from './ProjectCard'
import TaskList from './TaskList'
import type { Project } from './projectTypes'
import type { ProjectLoadStatus } from './projectTypes'
import styles from './ProjectListPage.module.css'

interface ProjectListPageProps {
  projects: Project[]
  status: ProjectLoadStatus
  onRetry: () => void
  onOpenCreate: () => void
  onOpenTagManage: () => void
}

type ProjectView = 'projects' | 'all' | 'unclassified'

interface TaskListState {
  mode: TaskListMode | null
  status: ProjectLoadStatus
  tasks: TaskResponse[]
}

const PROJECT_VIEWS: { value: ProjectView; label: string }[] = [
  { value: 'projects', label: '폴더' },
  { value: 'all', label: '최신순' },
  { value: 'unclassified', label: '미분류' },
]

export default function ProjectListPage({
  projects,
  status,
  onRetry,
  onOpenCreate,
  onOpenTagManage,
}: ProjectListPageProps) {
  const [searchParams, setSearchParams] = useSearchParams()
  const viewParam = searchParams.get('view')
  const activeView: ProjectView = viewParam === 'all' || viewParam === 'unclassified'
    ? viewParam
    : 'projects'
  const taskMode: TaskListMode | null = activeView === 'projects' ? null : activeView
  const [taskListState, setTaskListState] = useState<TaskListState>({
    mode: null,
    status: 'idle',
    tasks: [],
  })
  const [taskReloadKey, setTaskReloadKey] = useState(0)
  const [isDeleteMode, setIsDeleteMode] = useState(false)
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<number>>(new Set())
  const [isDeletingTasks, setIsDeletingTasks] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const folderNameById = useMemo(
    () => new Map(projects.map((project) => [project.id, project.name])),
    [projects],
  )

  useEffect(() => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
  }, [taskMode])

  useEffect(() => {
    if (taskMode === null) return

    const controller = new AbortController()
    setTaskListState({ mode: taskMode, status: 'loading', tasks: [] })

    void getTaskList(taskMode, controller.signal)
      .then((tasks) => {
        setTaskListState({ mode: taskMode, status: 'ready', tasks })
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setTaskListState({ mode: taskMode, status: 'error', tasks: [] })
      })

    return () => controller.abort()
  }, [taskMode, taskReloadKey])

  const changeView = (view: ProjectView) => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
    setSearchParams(view === 'projects' ? {} : { view })
  }

  const leaveDeleteMode = () => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
  }

  const toggleTaskSelection = (taskId: number) => {
    setSelectedTaskIds((current) => {
      const next = new Set(current)
      if (next.has(taskId)) next.delete(taskId)
      else next.add(taskId)
      return next
    })
    setDeleteError(null)
  }

  const removeSelectedTasks = async () => {
    if (selectedTaskIds.size === 0) return

    setIsDeletingTasks(true)
    setDeleteError(null)
    try {
      await deleteTasks({ taskIds: [...selectedTaskIds] })
      leaveDeleteMode()
      setTaskReloadKey((key) => key + 1)
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setDeleteError(apiMessage ?? '선택한 작업을 삭제하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsDeletingTasks(false)
    }
  }

  return (
    <section className={styles.page} aria-labelledby="projects-page-title">
      <header className={styles.heading}>
        <div>
          <h1 id="projects-page-title">폴더</h1>
          <p>진행 중인 할 일을 확인하고 관리합니다.</p>
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.secondary} onClick={onOpenTagManage}>
            <IconTags size={17} aria-hidden="true" />
            태그 관리
          </button>
          <ModalTriggerButton
            dialogId="create-project-dialog"
            icon={<IconPlus size={18} aria-hidden="true" />}
            onClick={onOpenCreate}
          >
            새 폴더
          </ModalTriggerButton>
        </div>
      </header>

      <div className={styles['view-toolbar']}>
        <nav className={styles['view-switcher']} aria-label="폴더 화면 전환">
          {PROJECT_VIEWS.map((view) => (
            <button
              type="button"
              className={activeView === view.value ? styles['view-button-active'] : styles['view-button']}
              aria-pressed={activeView === view.value}
              onClick={() => changeView(view.value)}
              key={view.value}
            >
              {view.label}
            </button>
          ))}
        </nav>
        <button
          type="button"
          className={`${styles.secondary} ${styles['filter-button']}`}
          disabled
          title="필터 · 준비 중"
        >
          <IconFilter size={17} aria-hidden="true" />
          필터
        </button>
      </div>

      {taskMode !== null ? (
        <div className={styles['task-content']}>
          {taskListState.mode !== taskMode
          || taskListState.status === 'loading'
          || taskListState.status === 'idle' ? (
            <div className={styles.state} role="status">
              <span className={styles['state-mark']} aria-hidden="true" />
              <p>할 일을 불러오고 있습니다.</p>
            </div>
          ) : taskListState.status === 'error' ? (
            <div className={styles.state}>
              <p>할 일 목록을 불러오지 못했습니다.</p>
              <button type="button" onClick={() => setTaskReloadKey((key) => key + 1)}>
                다시 불러오기
              </button>
            </div>
          ) : (
            <>
              <div className={styles['section-heading']}>
                <h2>{taskMode === 'all' ? '최신 할 일' : '미분류 할 일'}</h2>
                <div className={styles['task-list-actions']}>
                  <span>{taskListState.tasks.length}개</span>
                  <DeleteIconButton
                    label={isDeleteMode ? '할 일 삭제 선택 취소' : '할 일 삭제 선택'}
                    active={isDeleteMode}
                    disabled={isDeletingTasks}
                    onClick={() => {
                      if (isDeleteMode) leaveDeleteMode()
                      else setIsDeleteMode(true)
                    }}
                  >
                    {isDeleteMode ? '취소' : <span className="sr-only">Task 삭제 선택</span>}
                  </DeleteIconButton>
                  {isDeleteMode && (
                    <DeleteIconButton
                      label="선택한 Task 삭제"
                      disabled={selectedTaskIds.size === 0 || isDeletingTasks}
                      onClick={() => void removeSelectedTasks()}
                    >
                      {isDeletingTasks ? '삭제 중' : <span className="sr-only">선택한 Task 삭제</span>}
                    </DeleteIconButton>
                  )}
                </div>
              </div>
              {deleteError && <p className={styles['delete-error']} role="alert">{deleteError}</p>}
              <TaskList
                tasks={taskListState.tasks}
                emptyTitle={taskMode === 'all' ? '등록된 할 일이 없어요.' : '미분류 할 일이 없어요.'}
                emptyDescription={taskMode === 'all'
                  ? '할 일을 만들면 최신순으로 이곳에 표시됩니다.'
                  : '폴더에 연결되지 않은 할 일이 이곳에 표시됩니다.'}
                getMetaText={(task) => task.projectId === null
                  ? '미분류'
                  : folderNameById.get(task.projectId ?? -1) ?? '폴더'}
                isDeleteMode={isDeleteMode}
                selectedTaskIds={selectedTaskIds}
                isDeleting={isDeletingTasks}
                onTaskSelectionChange={toggleTaskSelection}
                onTaskUpdated={() => setTaskReloadKey((key) => key + 1)}
              />
            </>
          )}
        </div>
      ) : status === 'loading' || status === 'idle' ? (
        <div className={styles.state} role="status">
          <span className={styles['state-mark']} aria-hidden="true" />
          <p>폴더를 불러오고 있습니다.</p>
        </div>
      ) : status === 'error' ? (
        <div className={styles.state}>
          <p>폴더 목록을 불러오지 못했습니다.</p>
          <button type="button" onClick={onRetry}>다시 불러오기</button>
        </div>
      ) : (
        <>
          <div className={styles['section-heading']}>
            <h2>전체 폴더</h2>
            <span>{projects.length}개</span>
          </div>
          {projects.length === 0 ? (
            <div className={styles.empty}>
              <IconFolders size={28} stroke={1.5} aria-hidden="true" />
              <h3>폴더를 시작할 준비가 되었습니다.</h3>
              <p>새 폴더를 만들면 이곳에서 한눈에 확인할 수 있습니다.</p>
            </div>
          ) : (
            <div className={styles.grid}>
              {projects.map((project) => (
                <ProjectCard key={project.id} project={project} />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
