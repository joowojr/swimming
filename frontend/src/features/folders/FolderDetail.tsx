import {memo, useCallback, useEffect, useRef, useState} from 'react'
import {IconChevronRight} from '@tabler/icons-react'
import {Link, useMatch, useNavigate} from 'react-router-dom'
import type {ApiError} from '../../api/client'
import DeleteIconButton from '../../components/DeleteIconButton'
import LoadMoreButton from '../../components/LoadMoreButton'
import TaskFilterMenu from '../../components/TaskFilterMenu'
import FolderHeader from './FolderHeader'
import FolderViewSwitch from './FolderViewSwitch'
import FolderProgressToast from './FolderProgressToast'
import {
  EMPTY_TASK_FILTER,
  countActiveFilters,
  matchesTaskFilter,
} from '../tasks/taskFilter'
import type { TaskFilter } from '../tasks/taskFilter'
import CreateTaskComposer from '../tasks/CreateTaskComposer'
import { useTaskStore } from '../../store/taskStore'
import { useSourceStore } from '../../store/sourceStore'
import {deleteTasks, getFolderTasks} from '../tasks/taskApi'
import type { TaskSummaryResponse } from '../tasks/taskTypes'
import {getFolder} from './folderApi.ts'
import type {Folder, FolderDetail as FolderDetailData} from './folderTypes.ts'
import TaskList from '../tasks/TaskList'
import LinkFolderView from '../knowledge/LinkFolderView'
import styles from './FolderDetail.module.css'

interface FolderDetailProps {
  folderId: number | null
  onDeleted: (folderId: number) => void
}

type DetailState =
  | { status: 'loading' }
  | { status: 'ready'; folder: FolderDetailData }
  | { status: 'error'; notFound: boolean }

/** 할 일은 폴더와 따로 불러 이어 읽는다. 커서는 이 화면이 소유한다. */
type TaskPageState =
  | { status: 'loading' }
  | { status: 'ready'; items: TaskSummaryResponse[]; nextCursor: string | null; hasNext: boolean }
  | { status: 'error' }

// const taskFilters: Array<{ value: TaskFilter; label: string }> = [
//   { value: 'ALL', label: '전체' },
//   ...TASK_STATUS_VALUES.map((status) => ({
//     value: status,
//     label: TASK_STATUS_LABEL[status],
//   })),
// ]

function isNotFound(error: unknown) {
  return typeof error === 'object' && error !== null && (error as ApiError).status === 404
}

const FOLDER_DELETE_MESSAGE = '폴더를 삭제하려면 연결된 할 일과 저장한 링크를 모두 삭제해야 합니다. 노트는 유지됩니다.'

const FolderBreadcrumb = memo(function FolderBreadcrumb({ folder }: { folder: FolderDetailData }) {
  return (
    <nav className={styles.breadcrumb} aria-label="Breadcrumb">
      <Link to="/folders">폴더</Link>
      {folder.tag && (
        <>
          <IconChevronRight size={14} aria-hidden="true" />
          <span aria-current="page">{folder.tag.name}</span>
        </>
      )}
      <IconChevronRight size={14} aria-hidden="true" />
      <span aria-current="page">{folder.name}</span>
    </nav>
  )
})

export default function FolderDetail({ folderId, onDeleted }: FolderDetailProps) {
  const sourceFolderRevision = useSourceStore((state) => (
    folderId === null ? 0 : state.folderRevisionById[folderId] ?? 0
  ))
  const navigate = useNavigate()
  const removeTasksFromStore = useTaskStore((state) => state.remove)
  const isLinkView = useMatch('/folders/:folderId/links') !== null
  const navigateRef = useRef(navigate)
  const onDeletedRef = useRef(onDeleted)
  const [folderRequestKey, setFolderRequestKey] = useState(0)
  const [taskRequestKey, setTaskRequestKey] = useState(0)
  const [state, setState] = useState<DetailState>(
    folderId === null ? { status: 'error', notFound: true } : { status: 'loading' },
  )
  const [taskFilter, setTaskFilter] = useState<TaskFilter>(EMPTY_TASK_FILTER)
  const [isDeleteMode, setIsDeleteMode] = useState(false)
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<number>>(new Set())
  const [isDeletingTasks, setIsDeletingTasks] = useState(false)
  const [isLoadingMoreTasks, setIsLoadingMoreTasks] = useState(false)
  const [tasks, setTasks] = useState<TaskPageState>({ status: 'loading' })

  // 폴더가 바뀌면 이전 폴더의 할 일이 남지 않게 렌더 중에 되돌린다. 효과로 처리하면
  // 이전 목록을 한 번 그린 뒤 다시 그리게 된다.
  const [renderedFolderId, setRenderedFolderId] = useState(folderId)
  if (renderedFolderId !== folderId) {
    setRenderedFolderId(folderId)
    setTasks({ status: 'loading' })
  }
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const taskInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    navigateRef.current = navigate
    onDeletedRef.current = onDeleted
  }, [navigate, onDeleted])

  const leaveDeleteMode = () => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
  }

  const reloadTasks = useCallback(() => {
    setTasks({ status: 'loading' })
    setTaskRequestKey((key) => key + 1)
  }, [])


  const toggleTaskSelection = (taskId: number) => {
    setSelectedTaskIds((current) => {
      const next = new Set(current)
      if (next.has(taskId)) next.delete(taskId)
      else next.add(taskId)
      return next
    })
    setDeleteError(null)
  }

  /**
   * 다음 페이지를 이어 붙인다. 폴더 정보는 다시 부르지 않는다 — 할 일 목록과 갱신 시점이
   * 다르기 때문에 API가 나뉘어 있다.
   */
  const loadMoreTasks = async () => {
    if (folderId === null || tasks.status !== 'ready') return

    const cursor = tasks.nextCursor
    if (!cursor || isLoadingMoreTasks) return

    setIsLoadingMoreTasks(true)
    try {
      const page = await getFolderTasks(folderId, { cursor })
      setTasks((current) => current.status === 'ready'
        ? {
          status: 'ready',
          items: [...current.items, ...page.items],
          nextCursor: page.nextCursor,
          hasNext: page.hasNext,
        }
        : current)
    } catch {
      // 다음 장을 못 가져와도 이미 보이는 목록은 그대로 둔다.
    } finally {
      setIsLoadingMoreTasks(false)
    }
  }

  const removeSelectedTasks = async () => {
    if (selectedTaskIds.size === 0) return

    const taskIdsToDelete = new Set(selectedTaskIds)
    setIsDeletingTasks(true)
    setDeleteError(null)
    try {
      await deleteTasks({ taskIds: [...taskIdsToDelete] })
      // 매트릭스·할 일 페이지의 삭제와 같게, 사라진 task를 스토어에도 알린다.
      // 이 화면의 목록만 지우면 같은 task를 세는 다른 화면이 계속 들고 있는다.
      removeTasksFromStore([...taskIdsToDelete])
      setTasks((current) => {
        if (current.status !== 'ready') return current

        return {
          ...current,
          items: current.items.filter((task) => !taskIdsToDelete.has(task.id)),
        }
      })
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

  const retry = useCallback(() => {
    setState({ status: 'loading' })
    setFolderRequestKey((key) => key + 1)
  }, [])

  const handleFolderUpdated = useCallback((updated: Folder) => {
    setState((current) => current.status === 'ready'
      ? { status: 'ready', folder: { ...current.folder, ...updated } }
      : current)
  }, [])

  const handleFolderDeleted = useCallback((deletedFolderId: number) => {
    onDeletedRef.current(deletedFolderId)
    navigateRef.current('/folders', { replace: true })
  }, [])

  useEffect(() => {
    if (folderId === null) return

    let active = true

    void getFolder(folderId)
      .then((folder) => {
        if (active) setState({ status: 'ready', folder })
      })
      .catch((error: unknown) => {
        if (active) setState({ status: 'error', notFound: isNotFound(error) })
      })

    return () => { active = false }
  }, [folderId, folderRequestKey, sourceFolderRevision])

  useEffect(() => {
    if (folderId === null || isLinkView || tasks.status !== 'loading') return

    let active = true

    void getFolderTasks(folderId)
      .then((page) => {
        if (active) {
          setTasks({
            status: 'ready',
            items: page.items,
            nextCursor: page.nextCursor,
            hasNext: page.hasNext,
          })
        }
      })
      .catch(() => {
        if (active) setTasks({ status: 'error' })
      })

    return () => { active = false }
  }, [folderId, isLinkView, taskRequestKey, tasks.status])

  if (state.status === 'loading') {
    return (
      <section className={styles.page} aria-busy="true" aria-labelledby="folder-loading-title">
        <p className="sr-only" id="folder-loading-title" role="status">폴더 상세를 불러오고 있습니다.</p>
        <div className={styles['skeleton-breadcrumb']} aria-hidden="true" />
        <div className={styles['skeleton-heading']} aria-hidden="true" />
        <div className={styles['skeleton-copy']} aria-hidden="true" />
        <div className={styles['skeleton-summary']} aria-hidden="true" />
        <div className={styles['skeleton-list']} aria-hidden="true" />
      </section>
    )
  }

  if (state.status === 'error') {
    return (
      <section className={`${styles.page} ${styles['state-page']}`} aria-labelledby="folder-error-title">
        <p className={styles.eyebrow}>Folder detail</p>
        <h1 id="folder-error-title">
          {state.notFound ? '폴더를 찾을 수 없습니다.' : '폴더 상세를 불러오지 못했습니다.'}
        </h1>
        <p>
          {state.notFound
            ? '폴더 주소를 확인하거나 폴더 목록으로 돌아가 주세요.'
            : '연결 상태를 확인한 뒤 다시 불러와 주세요.'}
        </p>
        <div className={styles['state-actions']}>
          {!state.notFound && <button type="button" onClick={retry}>다시 불러오기</button>}
          <Link to="/folders">폴더 목록</Link>
        </div>
      </section>
    )
  }

  const { folder } = state
  const activeFilterCount = countActiveFilters(taskFilter)
  const taskItems = tasks.status === 'ready' ? tasks.items : []
  const visibleTasks = taskItems.filter((task) => matchesTaskFilter(task, taskFilter))
  const emptyCopy = activeFilterCount > 0
    ? {
      title: '조건에 맞는 할 일이 없어요.',
      description: '필터 조건을 바꾸면 다른 할 일을 볼 수 있어요.',
    }
    : {
      title: '등록된 할 일이 없어요.',
      description: '할 일이 추가되면 진행 순서대로 이곳에 표시됩니다.',
    }

  return (
    <article className={styles.page} aria-labelledby="folder-detail-title">
      <FolderBreadcrumb folder={folder} />

      <div className={styles['detail-main']}>

      <FolderHeader
        folder={folder}
        titleId="folder-detail-title"
        deleteMessage={FOLDER_DELETE_MESSAGE}
        onUpdated={handleFolderUpdated}
        onDeleted={handleFolderDeleted}
      />

      <FolderViewSwitch folderId={folder.id} current={isLinkView ? 'links' : 'tasks'} />

      <FolderProgressToast folderId={folder.id} folderName={folder.name} sourceCount={folder.sourceCount} />

      {/*<section className={styles.summary} aria-labelledby="folder-progress-title">*/}
      {/*  <span className={styles['journey-rail']} aria-hidden="true" style={progressStyle} />*/}
      {/*  <div className={styles['progress-content']}>*/}
      {/*    <div className={styles['progress-heading']}>*/}
      {/*      <h2 id="folder-progress-title">여정 진행</h2>*/}
      {/*      <strong>{completionPct}<span>%</span></strong>*/}
      {/*    </div>*/}
      {/*    <div*/}
      {/*      className={styles['progress-track']}*/}
      {/*      role="progressbar"*/}
      {/*      aria-label="폴더 진행률"*/}
      {/*      aria-valuemin={0}*/}
      {/*      aria-valuemax={100}*/}
      {/*      aria-valuenow={completionPct}*/}
      {/*    >*/}
      {/*      <span className={styles['progress-fill']} style={progressStyle} />*/}
      {/*    </div>*/}
      {/*    <p>*/}
      {/*      task {folder.progress.completedTaskCount} / {folder.progress.totalTaskCount} 완료*/}
      {/*      <span> · 지금까지 잘 오고 있어요</span>*/}
      {/*    </p>*/}
      {/*  </div>*/}
      {/*  <dl className={styles['target-date']}>*/}
      {/*    <div>*/}
      {/*      <dt>목표일</dt>*/}
      {/*      <dd>*/}
      {/*        {isEditingTargetDate ? (*/}
      {/*          <div className={styles['date-editor']}>*/}
      {/*            <input*/}
      {/*              type="date"*/}
      {/*              value={editValue}*/}
      {/*              aria-label="폴더 목표일"*/}
      {/*              aria-invalid={Boolean(editError)}*/}
      {/*              disabled={isSavingFolder}*/}
      {/*              autoFocus*/}
      {/*              onChange={(event) => { setEditValue(event.target.value); setEditError(null) }}*/}
      {/*              onBlur={() => void saveTargetDate(folder)}*/}
      {/*              onKeyDown={handleTargetDateEditorKeyDown}*/}
      {/*            />*/}
      {/*            {editError && <p role="alert">{editError}</p>}*/}
      {/*          </div>*/}
      {/*        ) : (*/}
      {/*          <button*/}
      {/*            type="button"*/}
      {/*            className={styles['editable-display']}*/}
      {/*            title="더블 클릭하여 목표일 수정"*/}
      {/*            onDoubleClick={() => startEditingTargetDate(folder.targetDate)}*/}
      {/*            onKeyDown={handleTargetDateDisplayKeyDown}*/}
      {/*          >*/}
      {/*            {formatTargetDate(folder.targetDate)}*/}
      {/*          </button>*/}
      {/*        )}*/}
      {/*      </dd>*/}
      {/*    </div>*/}
      {/*  </dl>*/}
      {/*</section>*/}

          {isLinkView ? (
            <LinkFolderView folderId={folder.id} />
          ) : (
            <section className={styles.tasks} aria-labelledby="folder-tasks-title">
        <div className={styles['section-heading']}>
          <div className={styles['section-title']}>
            <h2 id="folder-tasks-title">할 일</h2>
            <span>
              {activeFilterCount > 0
                ? `조건에 맞는 할 일 ${visibleTasks.length} / ${taskItems.length}개`
                : `할 일 ${taskItems.length}개`}
            </span>
          </div>
          <div className={styles['task-actions']}>
            <TaskFilterMenu
                value={taskFilter}
                onChange={setTaskFilter}
                triggerClassName={styles['task-filters']}
                iconSize={14}
            />
            <DeleteIconButton
                className={styles['compact-delete-button']}
                iconSize={14}
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
                    className={styles['compact-delete-button']}
                    iconSize={14}
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
        <div className={styles['task-list-stack']}>
          <CreateTaskComposer
              folderId={folder.id}
              inputRef={taskInputRef}
              variant="embedded"
              onCreated={() => {
                setTaskFilter(EMPTY_TASK_FILTER)
                reloadTasks()
              }}
          />
          <TaskList
            tasks={visibleTasks}
            emptyTitle={emptyCopy.title}
            emptyDescription={emptyCopy.description}
            connected
            isDeleteMode={isDeleteMode}
            selectedTaskIds={selectedTaskIds}
            isDeleting={isDeletingTasks}
            onTaskSelectionChange={toggleTaskSelection}
            onTaskUpdated={reloadTasks}
          />
          {tasks.status === 'ready' && tasks.hasNext && (
            <LoadMoreButton
              className={styles['load-more']}
              isLoading={isLoadingMoreTasks}
              onClick={() => void loadMoreTasks()}
            />
          )}
        </div>
            </section>
          )}
      </div>
    </article>
  )
}
