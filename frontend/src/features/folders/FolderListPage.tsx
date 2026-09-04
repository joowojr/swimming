import { useEffect, useMemo, useState } from 'react'
import { IconArrowsSort, IconFolders, IconPlus, IconTags } from '@tabler/icons-react'
import { useSearchParams } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import DeleteIconButton from '../../components/DeleteIconButton'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import ModeToggle from '../../components/ModeToggle'
import TaskFilterMenu from '../../components/TaskFilterMenu'
import {
  EMPTY_TASK_FILTER,
  countActiveFilters,
  matchesTaskFilter,
} from '../tasks/taskFilter'
import type { TaskFilter } from '../tasks/taskFilter'
import type { TaskSort } from '../tasks/taskTypes'
import { deleteTasks } from '../tasks/taskApi'
import { useTaskStore } from '../../store/taskStore'
import FolderCard from './FolderCard.tsx'
import TaskList from './TaskList'
import type { Folder } from './folderTypes.ts'
import type { FolderLoadStatus } from './folderTypes.ts'
import styles from './FolderListPage.module.css'

interface ProjectListPageProps {
  folders: Folder[]
  status: FolderLoadStatus
  onRetry: () => void
  onOpenCreate: () => void
  onOpenTagManage: () => void
}

type ProjectView = 'folders' | 'tasks'

const PROJECT_VIEWS: { value: ProjectView; label: string }[] = [
  { value: 'folders', label: '폴더' },
  { value: 'tasks', label: '할 일' },
]

export default function FolderListPage({
  folders,
  status,
  onRetry,
  onOpenCreate,
  onOpenTagManage,
}: ProjectListPageProps) {
  const [searchParams, setSearchParams] = useSearchParams()
  const viewParam = searchParams.get('view')
  const activeView: ProjectView = viewParam === 'tasks' ? 'tasks' : 'folders'
  const isTaskView = activeView === 'tasks'
  const tasksById = useTaskStore((state) => state.byId)
  const allTaskIds = useTaskStore((state) => state.allIds)
  const taskStatus = useTaskStore((state) => state.status)
  const loadAllTasks = useTaskStore((state) => state.loadAll)
  const removeTasksFromStore = useTaskStore((state) => state.remove)
  const [taskReloadKey, setTaskReloadKey] = useState(0)
  const [taskFilter, setTaskFilter] = useState<TaskFilter>(EMPTY_TASK_FILTER)
  const [taskSort, setTaskSort] = useState<TaskSort>('desc')
  const [isDeleteMode, setIsDeleteMode] = useState(false)
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<number>>(new Set())
  const [isDeletingTasks, setIsDeletingTasks] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const folderNameById = useMemo(
    () => new Map(folders.map((folder) => [folder.id, folder.name])),
    [folders],
  )

  useEffect(() => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
    setTaskFilter(EMPTY_TASK_FILTER)
  }, [activeView])

  // 정렬은 서버가 정하므로 바뀌면 다시 받는다. 미분류 여부는 받아둔 목록에서 거른다.
  useEffect(() => {
    if (!isTaskView) return
    void loadAllTasks(taskSort)
  }, [isTaskView, taskSort, taskReloadKey, loadAllTasks])

  const modeTasks = useMemo(() => {
    if (!isTaskView || allTaskIds === null) return []
    return allTaskIds.flatMap((taskId) => tasksById[taskId] ?? [])
  }, [isTaskView, allTaskIds, tasksById])

  const changeView = (view: ProjectView) => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
    setSearchParams(view === 'folders' ? {} : { view })
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
      removeTasksFromStore([...selectedTaskIds])
      leaveDeleteMode()
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setDeleteError(apiMessage ?? '선택한 작업을 삭제하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsDeletingTasks(false)
    }
  }

  const activeFilterCount = countActiveFilters(taskFilter)
  const visibleTasks = useMemo(
    () => modeTasks.filter((task) => matchesTaskFilter(task, taskFilter)),
    [modeTasks, taskFilter],
  )

  return (
    <section className={styles.page} aria-labelledby="folders-page-title">
      <header className={styles.heading}>
        <div>
          <h1 id="folders-page-title">폴더</h1>
          <p>진행 중인 할 일을 확인하고 관리합니다.</p>
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.secondary} onClick={onOpenTagManage}>
            <IconTags size={17} aria-hidden="true" />
            태그 관리
          </button>
          <ModalTriggerButton
            dialogId="create-folder-dialog"
            icon={<IconPlus size={18} aria-hidden="true" />}
            onClick={onOpenCreate}
          >
            새 폴더
          </ModalTriggerButton>
        </div>
      </header>

      <div className={styles['view-toolbar']}>
        <ModeToggle
          className={styles['view-switcher']}
          ariaLabel="폴더 화면 전환"
          options={PROJECT_VIEWS}
          value={activeView}
          onChange={changeView}
        />
        <div className={styles['task-tools']}>
          <TaskFilterMenu
            value={taskFilter}
            onChange={setTaskFilter}
            showFolderScope
            disabled={!isTaskView}
            triggerClassName={`${styles.secondary} ${styles['filter-button']}`}
            triggerTitle={isTaskView ? '할 일 필터' : '할 일 화면에서 쓸 수 있어요'}
          />
          <button
            type="button"
            className={`${styles.secondary} ${styles['filter-button']}`}
            disabled={!isTaskView}
            title={isTaskView ? '정렬 바꾸기' : '할 일 화면에서 쓸 수 있어요'}
            aria-label={taskSort === 'desc' ? '오래된순으로 정렬' : '최신순으로 정렬'}
            onClick={() => setTaskSort((current) => current === 'desc' ? 'asc' : 'desc')}
          >
            <IconArrowsSort size={17} aria-hidden="true" />
            {taskSort === 'desc' ? '최신순' : '오래된순'}
          </button>
        </div>
      </div>

      {isTaskView ? (
        <div className={styles['task-content']}>
          {taskStatus === 'loading' || taskStatus === 'idle' ? (
            <div className={styles.state} role="status">
              <span className={styles['state-mark']} aria-hidden="true" />
              <p>할 일을 불러오고 있습니다.</p>
            </div>
          ) : taskStatus === 'error' ? (
            <div className={styles.state}>
              <p>할 일 목록을 불러오지 못했습니다.</p>
              <button type="button" onClick={() => setTaskReloadKey((key) => key + 1)}>
                다시 불러오기
              </button>
            </div>
          ) : (
            <>
              <div className={styles['section-heading']}>
                <h2>{taskFilter.unclassifiedOnly ? '미분류 할 일' : '할 일'}</h2>
                <div className={styles['task-list-actions']}>
                  <span>
                    {activeFilterCount > 0
                      ? `${visibleTasks.length} / ${modeTasks.length}개`
                      : `${modeTasks.length}개`}
                  </span>
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
                tasks={visibleTasks}
                emptyTitle={activeFilterCount > 0
                  ? '조건에 맞는 할 일이 없어요.'
                  : '등록된 할 일이 없어요.'}
                emptyDescription={activeFilterCount > 0
                  ? '필터 조건을 바꾸면 다른 할 일을 볼 수 있어요.'
                  : '할 일을 만들면 이곳에 표시됩니다.'}
                getMetaText={(task) => task.folderId === null
                  ? '미분류'
                  : folderNameById.get(task.folderId ?? -1) ?? '폴더'}
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
            <span>{folders.length}개</span>
          </div>
          {folders.length === 0 ? (
            <div className={styles.empty}>
              <IconFolders size={28} stroke={1.5} aria-hidden="true" />
              <h3>폴더를 시작할 준비가 되었습니다.</h3>
              <p>새 폴더를 만들면 이곳에서 한눈에 확인할 수 있습니다.</p>
            </div>
          ) : (
            <div className={styles.grid}>
              {folders.map((folder) => (
                <FolderCard key={folder.id} folder={folder} />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
