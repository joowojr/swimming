import type {KeyboardEvent} from 'react'
import {useCallback, useEffect, useRef, useState} from 'react'
import {IconCalendarDue, IconChevronRight} from '@tabler/icons-react'
import {Link, useNavigate} from 'react-router-dom'
import type {ApiError} from '../../api/client'
import DdayChip from '../../components/DdayChip'
import DeleteIconButton from '../../components/DeleteIconButton'
import DeleteConfirmation from '../../components/DeleteConfirmation'
import InlineEditableText from '../../components/InlineEditableText'
import TaskFilterMenu from '../../components/TaskFilterMenu'
import {
  EMPTY_TASK_FILTER,
  countActiveFilters,
  matchesTaskFilter,
} from '../tasks/taskFilter'
import type { TaskFilter } from '../tasks/taskFilter'
import CreateTaskComposer from '../tasks/CreateTaskComposer'
import {deleteTasks} from '../tasks/taskApi'
import {deleteFolder, getFolder, updateFolder} from './folderApi.ts'
import {useFolderStore} from '../../store/folderStore.ts'
import type {FolderDetail as FolderDetailData, FolderStatus} from './folderTypes.ts'
import TaskList from '../tasks/TaskList'
import NoteCard from '../note/NoteCard'
import styles from './FolderDetail.module.css'

interface FolderDetailProps {
  folderId: number | null
  onDeleted: (folderId: number) => void
}

type DetailState =
  | { status: 'loading' }
  | { status: 'ready'; folder: FolderDetailData }
  | { status: 'error'; notFound: boolean }

type EditableFolderTextField = 'name' | 'description'

// const taskFilters: Array<{ value: TaskFilter; label: string }> = [
//   { value: 'ALL', label: '전체' },
//   ...TASK_STATUS_VALUES.map((status) => ({
//     value: status,
//     label: TASK_STATUS_LABEL[status],
//   })),
// ]

const folderStatusLabel: Record<FolderStatus, string> = {
  IN_PROGRESS: '진행 중',
  ARCHIVED: '보관됨',
}

const targetDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
})

function formatTargetDate(targetDate: string | null) {
  return targetDate
    ? targetDateFormatter.format(new Date(`${targetDate}T00:00:00`))
    : '설정하지 않음'
}

function isNotFound(error: unknown) {
  return typeof error === 'object' && error !== null && (error as ApiError).status === 404
}

export default function FolderDetail({ folderId, onDeleted }: FolderDetailProps) {
  const navigate = useNavigate()
  const applyFolderToStore = useFolderStore((state) => state.apply)
  const [requestKey, setRequestKey] = useState(0)
  const [state, setState] = useState<DetailState>(
    folderId === null ? { status: 'error', notFound: true } : { status: 'loading' },
  )
  const [taskFilter, setTaskFilter] = useState<TaskFilter>(EMPTY_TASK_FILTER)
  const [isDeleteMode, setIsDeleteMode] = useState(false)
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<number>>(new Set())
  const [isDeletingTasks, setIsDeletingTasks] = useState(false)
  const [isLoadingMoreTasks, setIsLoadingMoreTasks] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [isConfirmingFolderDelete, setIsConfirmingFolderDelete] = useState(false)
  const [isDeletingFolder, setIsDeletingFolder] = useState(false)
  const [folderDeleteError, setFolderDeleteError] = useState<string | null>(null)
  const [isEditingTargetDate, setIsEditingTargetDate] = useState(false)
  const [editValue, setEditValue] = useState('')
  const [editError, setEditError] = useState<string | null>(null)
  const [isSavingFolder, setIsSavingFolder] = useState(false)
  const taskInputRef = useRef<HTMLInputElement>(null)
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

  /**
   * 폴더 상세를 커서와 함께 다시 불러 할 일만 이어 붙인다. 진척과 폴더 정보는 새 응답으로
   * 갱신한다. 목록을 넘기는 사이에 다른 데서 바뀌었을 수 있어서다.
   */
  const loadMoreTasks = async () => {
    if (state.status !== 'ready') return

    const cursor = state.folder.tasks.nextCursor
    if (!cursor || isLoadingMoreTasks) return

    setIsLoadingMoreTasks(true)
    try {
      const next = await getFolder(state.folder.id, { cursor })
      setState((current) => current.status === 'ready'
        ? {
          status: 'ready',
          folder: {
            ...next,
            tasks: {
              items: [...current.folder.tasks.items, ...next.tasks.items],
              nextCursor: next.tasks.nextCursor,
              hasNext: next.tasks.hasNext,
            },
          },
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
      setState((current) => {
        if (current.status !== 'ready') return current

        const removed = current.folder.tasks.items.filter((task) => taskIdsToDelete.has(task.id))
        const items = current.folder.tasks.items.filter((task) => !taskIdsToDelete.has(task.id))

        // 진척은 폴더 전체 기준이다. 지금 보이는 페이지에서 다시 세면 안 되고,
        // 방금 지운 만큼만 덜어낸다.
        const totalTaskCount = current.folder.progress.totalTaskCount - removed.length
        const completedTaskCount = current.folder.progress.completedTaskCount
          - removed.filter((task) => task.status === 'DONE').length

        return {
          status: 'ready',
          folder: {
            ...current.folder,
            tasks: { ...current.folder.tasks, items },
            progress: {
              totalTaskCount,
              completedTaskCount,
              completionPct: totalTaskCount === 0
                ? 0
                : Math.floor(completedTaskCount * 100 / totalTaskCount),
            },
          },
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

  const removeFolder = async (folder: FolderDetailData) => {
    if (isDeletingFolder) return
    setIsDeletingFolder(true)
    setFolderDeleteError(null)
    try {
      await deleteFolder(folder.id)
      onDeleted(folder.id)
      navigate('/folders', { replace: true })
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setFolderDeleteError(apiMessage ?? '폴더를 삭제하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsDeletingFolder(false)
    }
  }

  const startEditingTargetDate = (value: string | null) => {
    if (isSavingFolder) return
    setIsEditingTargetDate(true)
    setEditValue(value ?? '')
    setEditError(null)
  }

  const cancelEditingTargetDate = () => {
    if (isSavingFolder) return
    setIsEditingTargetDate(false)
    setEditValue('')
    setEditError(null)
  }

  const applyUpdatedFolder = (folder: FolderDetailData, updated: Awaited<ReturnType<typeof updateFolder>>) => {
    applyFolderToStore(updated)
    setState({
      status: 'ready',
      folder: {
        ...folder,
        name: updated.name,
        description: updated.description,
        targetDate: updated.targetDate,
        status: updated.status,
        tag: updated.tag,
      },
    })
  }

  const saveFolderTextField = async (
    folder: FolderDetailData,
    field: EditableFolderTextField,
    value: string,
  ) => {
    setIsSavingFolder(true)
    try {
      const updated = await updateFolder(folder.id, {
        name: field === 'name' ? value : folder.name,
        description: field === 'description' ? value : folder.description,
        targetDate: folder.targetDate,
        status: folder.status,
        tagId: folder.tag?.id ?? null,
      })
      applyUpdatedFolder(folder, updated)
    } finally {
      setIsSavingFolder(false)
    }
  }

  const getFolderFieldError = (error: unknown, field: EditableFolderTextField) => {
    const apiError = typeof error === 'object' && error !== null ? error as ApiError : undefined
    return apiError?.errors?.[field]
      ?? apiError?.message
      ?? '폴더 정보를 저장하지 못했습니다.'
  }

  const saveTargetDate = async (folder: FolderDetailData) => {
    if (!isEditingTargetDate || isSavingFolder) return
    const targetDate = editValue || null
    if (targetDate === folder.targetDate) {
      cancelEditingTargetDate()
      return
    }

    setIsSavingFolder(true)
    setEditError(null)
    try {
      const updated = await updateFolder(folder.id, {
        name: folder.name,
        description: folder.description,
        targetDate,
        status: folder.status,
        tagId: folder.tag?.id ?? null,
      })
      applyUpdatedFolder(folder, updated)
      setIsEditingTargetDate(false)
      setEditValue('')
    } catch (error: unknown) {
      const apiError = typeof error === 'object' && error !== null ? error as ApiError : undefined
      setEditError(apiError?.errors?.targetDate ?? apiError?.message ?? '목표일을 저장하지 못했습니다.')
    } finally {
      setIsSavingFolder(false)
    }
  }

  const handleTargetDateDisplayKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Enter' && event.key !== 'F2') return
    event.preventDefault()
    startEditingTargetDate(state.status === 'ready' ? state.folder.targetDate : null)
  }

  const handleTargetDateEditorKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      cancelEditingTargetDate()
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      event.currentTarget.blur()
    }
  }

  const retry = useCallback(() => {
    setState({ status: 'loading' })
    setRequestKey((key) => key + 1)
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
  }, [folderId, requestKey])

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
  const visibleTasks = folder.tasks.items.filter((task) => matchesTaskFilter(task, taskFilter))
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
      <nav className={styles.breadcrumb} aria-label="Breadcrumb">
        <Link to="/folders">폴더</Link>
        {folder.tag && (
            <>
              <IconChevronRight size={14} aria-hidden="true" />
              <span aria-current="page">{folder.tag.name}</span>
            </>
        )}
        <IconChevronRight size={14} aria-hidden="true"/>
        <span aria-current="page">{folder.name}</span>
      </nav>

      <div className={styles['detail-layout']}>
        <div className={styles['detail-main']}>

      <header className={styles.header}>
        <div className={styles['header-top']}>
          <div className={styles.badges} data-tone={folder.id % 4}>
            {folder.tag && <span className={styles.tag}>{folder.tag.name}</span>}
            <span className={styles['folder-status']} data-status={folder.status}>{folderStatusLabel[folder.status]}</span>
            <DdayChip targetDate={folder.targetDate} />
          </div>
          <DeleteIconButton
            className={styles['compact-delete-button']}
            iconSize={14}
            label="폴더 삭제"
            active={isConfirmingFolderDelete}
            disabled={isDeletingFolder}
            onClick={() => {
              setIsConfirmingFolderDelete((current) => !current)
              setFolderDeleteError(null)
            }}
          >
            <span>{isConfirmingFolderDelete ? '취소' : '폴더 삭제'}</span>
          </DeleteIconButton>
        </div>
        {isConfirmingFolderDelete && (
          <DeleteConfirmation
            message="폴더를 삭제하려면 연결된 할 일을 모두 삭제해야 합니다. 메모는 유지됩니다."
            ariaLabel="폴더 삭제 확인"
            isDeleting={isDeletingFolder}
            onCancel={() => setIsConfirmingFolderDelete(false)}
            onConfirm={() => void removeFolder(folder)}
          />
        )}
        {folderDeleteError && <p className={styles['delete-error']} role="alert">{folderDeleteError}</p>}
        <div className={styles['editable-group']}>
          <h1 id="folder-detail-title">
            <InlineEditableText
              value={folder.name}
              ariaLabel="폴더 제목"
              maxLength={255}
              requiredMessage="폴더 이름을 입력해 주세요."
              disabled={isSavingFolder}
              onSave={(value) => saveFolderTextField(folder, 'name', value)}
              getErrorMessage={(error) => getFolderFieldError(error, 'name')}
            />
          </h1>
        </div>
        <div className={styles['editable-group']}>
          <p>
            <InlineEditableText
              value={folder.description}
              emptyText="폴더 설명이 아직 없습니다."
              ariaLabel="폴더 설명"
              requiredMessage="폴더 설명을 입력해 주세요."
              disabled={isSavingFolder}
              onSave={(value) => saveFolderTextField(folder, 'description', value)}
              getErrorMessage={(error) => getFolderFieldError(error, 'description')}
            />
          </p>
        </div>
        <div className={styles['header-bottom']}>
          {isEditingTargetDate ? (
            <span className={styles['date-editor']}>
              <input
                type="date"
                value={editValue}
                aria-label="폴더 목표일"
                aria-invalid={Boolean(editError)}
                disabled={isSavingFolder}
                autoFocus
                onChange={(event) => { setEditValue(event.target.value); setEditError(null) }}
                onBlur={() => void saveTargetDate(folder)}
                onKeyDown={handleTargetDateEditorKeyDown}
              />
            </span>
          ) : (
            <button
              type="button"
              className={styles['target-date-chip']}
              title="더블 클릭하여 목표일 수정"
              disabled={isSavingFolder}
              onDoubleClick={() => startEditingTargetDate(folder.targetDate)}
              onKeyDown={handleTargetDateDisplayKeyDown}
            >
              <IconCalendarDue size={14} stroke={1.8} aria-hidden="true" />
              <span>
                <span className="sr-only">목표일 </span>
                {formatTargetDate(folder.targetDate)}
              </span>
            </button>
          )}
        </div>
        {editError && <p className={styles['target-date-error']} role="alert">{editError}</p>}
      </header>

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

          <section className={styles.tasks} aria-labelledby="folder-tasks-title">
        <div className={styles['section-heading']}>
          <div className={styles['section-title']}>
            <h2 id="folder-tasks-title">할 일</h2>
            <span>
              {activeFilterCount > 0
                ? `조건에 맞는 할 일 ${visibleTasks.length} / ${folder.tasks.items.length}개`
                : `총 ${folder.progress.totalTaskCount}개의 할 일이 있어요`}
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
                setRequestKey((key) => key + 1)
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
            onTaskUpdated={() => setRequestKey((key) => key + 1)}
          />
          {folder.tasks.hasNext && (
            <button
              type="button"
              className={styles['load-more']}
              disabled={isLoadingMoreTasks}
              onClick={() => void loadMoreTasks()}
            >
              {isLoadingMoreTasks ? '불러오는 중' : '더 보기'}
            </button>
          )}
        </div>
      </section>
        </div>

        <aside className={styles['detail-aside']} aria-label="폴더 메모">
          <NoteCard key={folder.id} folders={[folder]} folderId={folder.id} />
        </aside>
      </div>
    </article>
  )
}
