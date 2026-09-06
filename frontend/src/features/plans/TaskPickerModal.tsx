import type { TaskSummaryResponse } from '../tasks/taskTypes'
import { useEffect, useRef, useState } from 'react'
import { IconCheck, IconLoader2, IconPlus, IconX } from '@tabler/icons-react'
import type { MouseEvent } from 'react'
import ModeToggle from '../../components/ModeToggle'
import { getFolder } from '../folders/folderApi.ts'
import type { FolderDetail } from '../folders/folderTypes.ts'
import { useFolderStore } from '../../store/folderStore.ts'
import styles from './TaskPickerModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'

interface TaskPickerModalProps {
  selectedTaskIds: ReadonlySet<number>
  onAdd: (tasks: TaskSummaryResponse[]) => Promise<void>
  onAddTask: (title: string, folderId: number | null, priority: boolean, urgent: boolean, planDate: string | null) => Promise<void>
  onClose: () => void
  initialPriority?: boolean
  initialUrgent?: boolean
  initialPlanDate?: string
}

/** 선택한 폴더의 상세를 불러오는 상태. idle은 아직 폴더를 고르지 않은 상태다. */
type DetailStatus = 'idle' | 'loading' | 'ready' | 'error'

type AddMode = 'direct' | 'folder'

const ADD_MODE_OPTIONS = [
  {
    value: 'direct',
    label: '직접 추가',
    id: 'direct-add-tab',
    controls: 'direct-add-panel',
  },
  {
    value: 'folder',
    label: '폴더에서 선택',
    id: 'folder-add-tab',
    controls: 'folder-add-panel',
  },
] as const

// const TIME_PERIOD_CHIPS = [
//   { label: '아침', emoji: '🌅' },
//   { label: '오후', emoji: '🌤️' },
//   { label: '밤', emoji: '🌙' },
//   { label: '새벽', emoji: '🌌' },
//   { label: '직접 해제', emoji: '🕰️' },
// ]

const PRIORITY_CHIPS = [
  { label: '중요', emoji: '📌' },
]

const URGENCY_CHIPS = [
  { label: '즉시', emoji: '⚡' },
]

export default function TaskPickerModal({
  selectedTaskIds,
  onAdd,
  onAddTask,
  onClose,
  initialPriority = false,
  initialUrgent = false,
  initialPlanDate = '',
}: TaskPickerModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const folders = useFolderStore((state) => state.folders)
  // 한 번 불러온 폴더 상세는 모달이 닫힐 때까지 재사용한다.
  const [detailCache, setDetailCache] = useState<Record<number, FolderDetail>>({})
  const [failedFolderIds, setFailedFolderIds] = useState<ReadonlySet<number>>(new Set())
  const [addMode, setAddMode] = useState<AddMode>('direct')
  const [title, setTitle] = useState('')
  const [folderId, setProjectId] = useState('')
  const [taskProjectId, setTaskProjectId] = useState('')
  const [pendingTaskIds, setPendingTaskIds] = useState<Set<number>>(new Set())
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [taskSubmitError, setTaskSubmitError] = useState<string | null>(null)
  const [isLoadingMoreTasks, setIsLoadingMoreTasks] = useState(false)
  const [selectedPriority, setSelectedPriority] = useState<string | null>(initialPriority ? '중요' : null)
  const [selectedUrgent, setSelectedUrgent] = useState(initialUrgent)
  const [planDate, setPlanDate] = useState(initialPlanDate)

  const activeFolderId = taskProjectId ? Number(taskProjectId) : null
  const activeProject = activeFolderId === null ? undefined : detailCache[activeFolderId]
  const detailStatus: DetailStatus = activeFolderId === null
    ? 'idle'
    : activeProject
      ? 'ready'
      : failedFolderIds.has(activeFolderId)
        ? 'error'
        : 'loading'
  const pendingTasks = Object.values(detailCache).flatMap((detail) => detail.tasks.items
    .filter((task) => pendingTaskIds.has(task.id))
    .map((task) => ({ task, folderName: detail.name })))
  const totalTaskCount = addMode === 'direct'
    ? (title.trim() ? 1 : 0)
    : pendingTaskIds.size

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    dialog.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  useEffect(() => {
    if (activeFolderId === null) return
    if (detailCache[activeFolderId] || failedFolderIds.has(activeFolderId)) return

    let active = true
    void getFolder(activeFolderId)
      .then((detail) => {
        if (active) setDetailCache((current) => ({ ...current, [detail.id]: detail }))
      })
      .catch(() => {
        if (active) setFailedFolderIds((current) => new Set(current).add(activeFolderId))
      })
    return () => { active = false }
  }, [activeFolderId, detailCache, failedFolderIds])

  /** 고른 폴더의 다음 할 일 페이지를 캐시에 이어 붙인다. */
  const loadMoreTasks = async () => {
    const cursor = activeProject?.tasks.nextCursor
    if (!activeProject || !cursor || isLoadingMoreTasks) return

    setIsLoadingMoreTasks(true)
    try {
      const next = await getFolder(activeProject.id, { cursor })
      setDetailCache((current) => {
        const cached = current[next.id]
        if (!cached) return current

        return {
          ...current,
          [next.id]: {
            ...next,
            tasks: {
              items: [...cached.tasks.items, ...next.tasks.items],
              nextCursor: next.tasks.nextCursor,
              hasNext: next.tasks.hasNext,
            },
          },
        }
      })
    } catch {
      // 다음 장을 못 가져와도 이미 고른 것과 보이는 목록은 그대로 둔다.
    } finally {
      setIsLoadingMoreTasks(false)
    }
  }

  const requestClose = () => {
    if (!isSubmitting) dialogRef.current?.close()
  }

  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const toggleTask = (taskId: number) => {
    setTaskSubmitError(null)
    setPendingTaskIds((current) => {
      const next = new Set(current)
      if (next.has(taskId)) next.delete(taskId)
      else next.add(taskId)
      return next
    })
  }

  const selectAddMode = (mode: AddMode) => {
    setAddMode(mode)
    setTaskSubmitError(null)
  }

  const addAllTasks = async () => {
    const trimmedTitle = title.trim()
    if (totalTaskCount === 0 || isSubmitting) return

    setIsSubmitting(true)
    setTaskSubmitError(null)

    try {
      if (addMode === 'direct') {
        await onAddTask(
          trimmedTitle,
          folderId ? Number(folderId) : null,
          selectedPriority === '중요',
          selectedUrgent,
          planDate || null,
        )
      } else {
        await onAdd(pendingTasks.map(({ task }) => task))
      }
      dialogRef.current?.close()
    } catch {
      setTaskSubmitError('할 일을 추가하지 못했습니다. 입력과 선택 내용을 확인해 주세요.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <dialog
      id="task-picker-dialog"
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      aria-labelledby="task-picker-title"
      aria-busy={isSubmitting}
      onCancel={(event) => { if (isSubmitting) event.preventDefault() }}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <form
        className={`${styles.modal} ${modalStyles.surface}`}
        onSubmit={(event) => {
          event.preventDefault()
          void addAllTasks()
        }}
      >
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="task-picker-title">할 일 추가</h2>
            <p>새 할 일을 만들거나 폴더별 할 일을 골라 주세요.</p>
          </div>
          <button type="button" aria-label="Task 선택 창 닫기" disabled={isSubmitting} onClick={requestClose}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <ModeToggle
            ariaLabel="할 일 추가 방식"
            options={ADD_MODE_OPTIONS}
            value={addMode}
            disabled={isSubmitting}
            fullWidth
            semantics="tabs"
            onChange={selectAddMode}
          />
          <section className={styles['planning-option-field']} aria-label="할 일 계획 옵션">
            <label className={styles['date-option']}>
              <input aria-label="계획 날짜" type="date" value={planDate} onChange={(event) => setPlanDate(event.target.value)} disabled={isSubmitting} />
            </label>
            {addMode === 'direct' && (
              <div className={styles['planning-option-chips']} role="list">
                {URGENCY_CHIPS.map((option) => (
                  <span role="listitem" key={option.label}>
                    <button
                      type="button"
                      aria-pressed={selectedUrgent}
                      disabled={isSubmitting}
                      onClick={() => setSelectedUrgent((current) => !current)}
                    >
                      <span aria-hidden="true">{option.emoji}</span>
                      {option.label}
                    </button>
                  </span>
                ))}
                {PRIORITY_CHIPS.map((option) => (
                  <span role="listitem" key={option.label}>
                    <button
                      type="button"
                      aria-pressed={selectedPriority === option.label}
                      disabled={isSubmitting}
                      onClick={() => setSelectedPriority((current) => (
                        current === option.label ? null : option.label
                      ))}
                    >
                      <span aria-hidden="true">{option.emoji}</span>
                      {option.label}
                    </button>
                  </span>
                ))}
              </div>
            )}
          </section>
          <section
            id="direct-add-panel"
            className={`${styles['mode-panel']} ${styles['quick-add']}`}
            role="tabpanel"
            aria-labelledby="direct-add-tab"
            hidden={addMode !== 'direct'}
          >
            <label htmlFor="daily-plan-ad-hoc-title">새 할 일</label>
            <select
              aria-label="할 일을 추가할 폴더"
              value={folderId}
              disabled={isSubmitting}
              onChange={(event) => {
                setProjectId(event.target.value)
                setTaskSubmitError(null)
              }}
            >
              <option value="">미분류</option>
              {folders.map((folder) => (
                <option value={folder.id} key={folder.id}>{folder.name}</option>
              ))}
            </select>
            <input
              id="daily-plan-ad-hoc-title"
              value={title}
              maxLength={255}
              placeholder="할 일을 입력해 주세요"
              disabled={isSubmitting}
              onChange={(event) => {
                setTitle(event.target.value)
                setTaskSubmitError(null)
              }}
            />
          </section>
          <section
            id="folder-add-panel"
            className={`${styles['mode-panel']} ${styles['folder-add']}`}
            role="tabpanel"
            aria-labelledby="folder-add-tab"
            hidden={addMode !== 'folder'}
          >
            <section className={styles['task-select']} aria-labelledby="task-select-label">
              <label id="task-select-label" htmlFor="daily-plan-task-folder">폴더</label>
              <select
                id="daily-plan-task-folder"
                value={taskProjectId}
                disabled={isSubmitting}
                onChange={(event) => setTaskProjectId(event.target.value)}
              >
                <option value="">폴더</option>
                {folders.map((folder) => (
                  <option value={folder.id} key={folder.id}>{folder.name}</option>
                ))}
              </select>

              {detailStatus === 'loading' ? (
                <p className={styles.state} role="status">
                  <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
                  작업을 불러오는 중…
                </p>
              ) : detailStatus === 'error' ? (
                <p className={styles.state} role="alert">작업을 불러오지 못했습니다. 잠시 후 다시 열어 주세요.</p>
              ) : !taskProjectId ? (
                <p className={styles.state}>폴더를 선택하면 할 일을 확인할 수 있습니다.</p>
              ) : !activeProject || activeProject.tasks.items.length === 0 ? (
                <p className={styles.state}>이 폴더에는 선택할 할 일이 없습니다.</p>
              ) : (
                <div className={styles.group}>
                  <ul>
                    {activeProject.tasks.items.map((task) => {
                      const alreadyAdded = selectedTaskIds.has(task.id)
                      const pending = pendingTaskIds.has(task.id)
                      return (
                        <li className={alreadyAdded || pending ? styles.selected : undefined} key={task.id}>
                          <span>
                            {(task.priority || task.urgent) && (
                              <span aria-label={`${task.urgent ? '즉시 ' : ''}${task.priority ? '중요' : ''}`}>
                                {task.urgent ? '⚡' : ''}{task.priority ? '📌' : ''}
                              </span>
                            )} {task.title}
                          </span>
                          <button
                            type="button"
                            aria-pressed={pending}
                            disabled={alreadyAdded || isSubmitting}
                            onClick={() => toggleTask(task.id)}
                          >
                            {pending
                              ? <IconCheck size={16} aria-hidden="true" />
                              : !alreadyAdded && <IconPlus size={16} aria-hidden="true" />}
                            {alreadyAdded ? '추가됨' : pending ? '' : ''}
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                  {activeProject.tasks.hasNext && (
                    <button
                      type="button"
                      className={styles['load-more']}
                      disabled={isLoadingMoreTasks || isSubmitting}
                      onClick={() => void loadMoreTasks()}
                    >
                      {isLoadingMoreTasks ? '불러오는 중' : '더 보기'}
                    </button>
                  )}
                </div>
              )}
            </section>
            {pendingTasks.length > 0 && (
              <section className={styles['selected-tasks']} aria-labelledby="selected-tasks-title">
                <div className={styles['selected-tasks-heading']}>
                  <h3 id="selected-tasks-title">선택한 할 일</h3>
                  <span>{pendingTasks.length}개</span>
                </div>
                <ul>
                  {pendingTasks.map(({ task, folderName }) => (
                    <li key={task.id}>
                      <span>
                        <small>{folderName}</small>
                        <strong>{task.title}</strong>
                      </span>
                      <button
                        type="button"
                        aria-label={`${folderName}의 ${task.title} 선택 해제`}
                        disabled={isSubmitting}
                        onClick={() => toggleTask(task.id)}
                      >
                        <IconX size={16} aria-hidden="true" />
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </section>
        </div>

        <footer className={styles.footer}>
          {taskSubmitError && <p className={styles.error} role="alert">{taskSubmitError}</p>}
          <button
            type="submit"
            disabled={totalTaskCount === 0 || isSubmitting}
          >
            {isSubmitting && (
              <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
            )}
            {isSubmitting
              ? '추가 중…'
              : addMode === 'direct'
                ? '할 일 추가'
                : `선택한 할 일 ${pendingTaskIds.size}개 추가`}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
