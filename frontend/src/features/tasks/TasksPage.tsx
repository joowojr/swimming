import { useEffect, useMemo, useState } from 'react'
import { IconArrowsSort } from '@tabler/icons-react'
import { useSearchParams } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import AddItemButton from '../../components/AddItemButton'
import DeleteIconButton from '../../components/DeleteIconButton'
import FolderLink from '../../components/FolderLink'
import ModeToggle from '../../components/ModeToggle'
import TaskFilterMenu from '../../components/TaskFilterMenu'
import TaskPickerModal from '../plans/TaskPickerModal'
import TaskList from './TaskList'
import TaskMatrix from './TaskMatrix'
import type { Folder } from '../folders/folderTypes'
import { useDailyPlanStore } from '../../store/dailyPlanStore'
import { useTaskStore } from '../../store/taskStore'
import { createTaskWithOptionalPlan, deleteTasks } from './taskApi'
import {
  EMPTY_TASK_FILTER,
  countActiveFilters,
  matchesTaskFilter,
} from './taskFilter'
import type { TaskFilter } from './taskFilter'
import type { TaskSort } from './taskTypes'
import styles from './TasksPage.module.css'

interface TasksPageProps {
  folders: Folder[]
}

type TaskView = 'matrix' | 'list'

const TASK_VIEWS: { value: TaskView; label: string }[] = [
  { value: 'matrix', label: '매트릭스' },
  { value: 'list', label: '리스트' },
]

export default function TasksPage({ folders }: TasksPageProps) {
  const [searchParams, setSearchParams] = useSearchParams()
  const activeView: TaskView = searchParams.get('view') === 'list' ? 'list' : 'matrix'
  const isListView = activeView === 'list'
  const tasksById = useTaskStore((state) => state.byId)
  const allTaskIds = useTaskStore((state) => state.allIds)
  const taskStatus = useTaskStore((state) => state.status)
  const loadAllTasks = useTaskStore((state) => state.loadAll)
  const removeTasksFromStore = useTaskStore((state) => state.remove)
  const invalidatePlanDate = useDailyPlanStore((state) => state.invalidateDate)
  const [taskReloadKey, setTaskReloadKey] = useState(0)
  const [taskFilter, setTaskFilter] = useState<TaskFilter>(EMPTY_TASK_FILTER)
  const [taskSort, setTaskSort] = useState<TaskSort>('desc')
  const [isDeleteMode, setIsDeleteMode] = useState(false)
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<number>>(new Set())
  const [isDeletingTasks, setIsDeletingTasks] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [isAdding, setIsAdding] = useState(false)
  const folderNameById = useMemo(
    () => new Map(folders.map((folder) => [folder.id, folder.name])),
    [folders],
  )

  // 화면이 바뀌면 삭제 선택과 필터를 초기화한다. 효과가 아니라 렌더 중에 이전 값과
  // 비교해 맞춘다. 효과로 처리하면 초기화 전 상태로 한 번 그린 뒤 다시 그리게 된다.
  const [renderedView, setRenderedView] = useState(activeView)
  if (renderedView !== activeView) {
    setRenderedView(activeView)
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
    setTaskFilter(EMPTY_TASK_FILTER)
  }

  // 정렬은 서버가 정하므로 바뀌면 다시 받는다. 미분류 여부는 받아둔 목록에서 거른다.
  useEffect(() => {
    if (!isListView) return
    void loadAllTasks(taskSort)
  }, [isListView, taskSort, taskReloadKey, loadAllTasks])

  const listTasks = useMemo(() => {
    if (!isListView || allTaskIds === null) return []
    return allTaskIds.flatMap((taskId) => tasksById[taskId] ?? [])
  }, [isListView, allTaskIds, tasksById])

  const changeView = (view: TaskView) => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
    setSearchParams(view === 'matrix' ? {} : { view })
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
    () => listTasks.filter((task) => matchesTaskFilter(task, taskFilter)),
    [listTasks, taskFilter],
  )

  return (
    <section className={styles.page} aria-labelledby="tasks-page-title">
      <header className={styles.heading}>
        <div>
          <h1 id="tasks-page-title">할 일</h1>
          <p>매트릭스로 우선순위를 잡고, 리스트로 전체를 훑어봅니다.</p>
        </div>
      </header>

      <div className={styles['view-toolbar']}>
        <ModeToggle
          ariaLabel="할 일 보기 방식"
          options={TASK_VIEWS}
          value={activeView}
          onChange={changeView}
        />
        <div className={styles['task-tools']}>
          <TaskFilterMenu
            value={taskFilter}
            onChange={setTaskFilter}
            showFolderScope={isListView}
            showFlags={isListView}
            triggerClassName={`${styles.secondary} ${styles['filter-button']}`}
            triggerTitle="할 일 필터"
          />
          {isListView && (
            <button
              type="button"
              className={`${styles.secondary} ${styles['filter-button']}`}
              title="정렬 바꾸기"
              aria-label={taskSort === 'desc' ? '오래된순으로 정렬' : '최신순으로 정렬'}
              onClick={() => setTaskSort((current) => current === 'desc' ? 'asc' : 'desc')}
            >
              <IconArrowsSort size={17} aria-hidden="true" />
              {taskSort === 'desc' ? '최신순' : '오래된순'}
            </button>
          )}
        </div>
      </div>

      {!isListView ? (
        <div className={styles['matrix-area']}>
          <TaskMatrix statusFilter={taskFilter.status} />
        </div>
      ) : taskStatus === 'loading' || taskStatus === 'idle' ? (
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
        <div className={styles['task-content']}>
          <div className={styles['section-heading']}>
            <h2>{taskFilter.unclassifiedOnly ? '미분류 할 일' : '할 일'}</h2>
            <div className={styles['task-list-actions']}>
              <span>
                {activeFilterCount > 0
                  ? `${visibleTasks.length} / ${listTasks.length}개`
                  : `${listTasks.length}개`}
              </span>
              <AddItemButton
                type="button"
                aria-label="할 일 추가"
                onClick={() => setIsAdding(true)}
              />
              <DeleteIconButton
                label={isDeleteMode ? '할 일 삭제 선택 취소' : '할 일 삭제 선택'}
                active={isDeleteMode}
                hideIcon={isDeleteMode}
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
            getMeta={(task) => task.folderId === null ? undefined : (
              <FolderLink folderId={task.folderId} name={folderNameById.get(task.folderId)} />
            )}
            isDeleteMode={isDeleteMode}
            selectedTaskIds={selectedTaskIds}
            isDeleting={isDeletingTasks}
            onTaskSelectionChange={toggleTaskSelection}
            onTaskUpdated={() => setTaskReloadKey((key) => key + 1)}
          />
        </div>
      )}

      {isAdding && (
        <TaskPickerModal
          selectedTaskIds={new Set()}
          onAdd={async () => undefined}
          onAddTask={async (title, folderId, priority, urgent, planDate) => {
            await createTaskWithOptionalPlan({ title, priority, urgent, folderId, planDate })
            // 생성 응답에 계획 항목이 없어 로컬 패치가 안 된다. 그 달을 다시 받게 한다.
            if (planDate) invalidatePlanDate(planDate)
            setTaskReloadKey((key) => key + 1)
          }}
          onClose={() => setIsAdding(false)}
        />
      )}
    </section>
  )
}
