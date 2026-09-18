import type { TaskSummaryResponse } from '../tasks/taskTypes'
import { useEffect, useRef, useState } from 'react'
import { IconCheck, IconLoader2, IconPlus, IconX } from '@tabler/icons-react'
import type { MouseEvent } from 'react'
import ActionButton from '../../components/ActionButton'
import LoadMoreButton from '../../components/LoadMoreButton'
import ModeToggle from '../../components/ModeToggle'
import { getFolderTasks } from '../tasks/taskApi'
import type { CursorPage } from '../../api/types'
import { useFolderStore } from '../../store/folderStore.ts'
import styles from './TaskPickerModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'

/** 우측에 담아 둔 "새로 만들 할 일" 하나. 저장 전까지는 서버에 없다. */
export interface NewTaskDraft {
  title: string
  folderId: number | null
  priority: boolean
  urgent: boolean
}

/**
 * "모두 추가"로 넘어가는 한 묶음.
 * existingTaskIds는 폴더에서 고른 이미 있는 할 일, newTasks는 좌측에서 새로 적은 할 일이다.
 */
export interface TaskPickerSubmission {
  existingTaskIds: number[]
  newTasks: NewTaskDraft[]
  planDate: string | null
}

interface TaskPickerModalProps {
  selectedTaskIds: ReadonlySet<number>
  /** 담은 것을 한 번에 저장한다. 실패하면 모달을 닫지 않고 담은 목록을 그대로 둔다. */
  onAddTasks: (submission: TaskPickerSubmission) => Promise<void>
  onClose: () => void
  initialPriority?: boolean
  initialUrgent?: boolean
  initialPlanDate?: string
}

/** 선택한 폴더의 상세를 불러오는 상태. idle은 아직 폴더를 고르지 않은 상태다. */
type DetailStatus = 'idle' | 'loading' | 'ready' | 'error'

type AddMode = 'direct' | 'folder'

/**
 * 우측 목록의 한 줄. 새로 적은 것과 폴더에서 고른 것을 한 목록에서 보여주되,
 * 저장할 때는 서로 다른 경로로 나가므로 kind로 구분한다.
 */
type StagedTask =
  | { kind: 'new'; key: string; title: string; folderId: number | null; priority: boolean; urgent: boolean }
  | { kind: 'existing'; key: string; taskId: number; title: string; folderName: string; priority: boolean; urgent: boolean }

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

const PRIORITY_CHIPS = [
  { label: '중요', emoji: '📌' },
]

const URGENCY_CHIPS = [
  { label: '즉시', emoji: '⚡' },
]

function existingKey(taskId: number) {
  return `existing-${taskId}`
}

export default function TaskPickerModal({
  selectedTaskIds,
  onAddTasks,
  onClose,
  initialPriority = false,
  initialUrgent = false,
  initialPlanDate = '',
}: TaskPickerModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleInputRef = useRef<HTMLInputElement>(null)
  const folders = useFolderStore((state) => state.folders)
  // 한 번 불러온 폴더의 할 일은 모달이 닫힐 때까지 재사용한다. 폴더 이름은 스토어에
  // 이미 있으므로 폴더 상세를 부르지 않고 목록만 받는다.
  const [taskCache, setTaskCache] = useState<Record<number, CursorPage<TaskSummaryResponse>>>({})
  const [failedFolderIds, setFailedFolderIds] = useState<ReadonlySet<number>>(new Set())
  const [addMode, setAddMode] = useState<AddMode>('direct')
  const [title, setTitle] = useState('')
  const [folderId, setProjectId] = useState('')
  const [taskProjectId, setTaskProjectId] = useState('')
  const [staged, setStaged] = useState<StagedTask[]>([])
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [taskSubmitError, setTaskSubmitError] = useState<string | null>(null)
  const [isLoadingMoreTasks, setIsLoadingMoreTasks] = useState(false)
  const [selectedPriority, setSelectedPriority] = useState<string | null>(initialPriority ? '중요' : null)
  const [selectedUrgent, setSelectedUrgent] = useState(initialUrgent)
  const [planDate, setPlanDate] = useState(initialPlanDate)
  // 새 줄마다 다른 key가 필요하다. 제목이 같은 할 일을 두 번 담을 수 있기 때문이다.
  const draftSeq = useRef(0)

  const activeFolderId = taskProjectId ? Number(taskProjectId) : null
  const activeProject = activeFolderId === null ? undefined : taskCache[activeFolderId]
  const detailStatus: DetailStatus = activeFolderId === null
    ? 'idle'
    : activeProject
      ? 'ready'
      : failedFolderIds.has(activeFolderId)
        ? 'error'
        : 'loading'
  const folderNameById = new Map(folders.map((folder) => [folder.id, folder.name]))
  const stagedExistingIds = new Set(
    staged.flatMap((entry) => (entry.kind === 'existing' ? [entry.taskId] : [])),
  )
  const canStageTitle = title.trim().length > 0

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
    if (taskCache[activeFolderId] || failedFolderIds.has(activeFolderId)) return

    let active = true
    void getFolderTasks(activeFolderId)
      .then((page) => {
        if (active) setTaskCache((current) => ({ ...current, [activeFolderId]: page }))
      })
      .catch(() => {
        if (active) setFailedFolderIds((current) => new Set(current).add(activeFolderId))
      })
    return () => { active = false }
  }, [activeFolderId, taskCache, failedFolderIds])

  /** 고른 폴더의 다음 할 일 페이지를 캐시에 이어 붙인다. */
  const loadMoreTasks = async () => {
    const cursor = activeProject?.nextCursor
    if (activeFolderId === null || !cursor || isLoadingMoreTasks) return

    setIsLoadingMoreTasks(true)
    try {
      const page = await getFolderTasks(activeFolderId, { cursor })
      setTaskCache((current) => {
        const cached = current[activeFolderId]
        if (!cached) return current

        return {
          ...current,
          [activeFolderId]: {
            items: [...cached.items, ...page.items],
            nextCursor: page.nextCursor,
            hasNext: page.hasNext,
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

  /**
   * 좌측에 적은 것을 우측 목록으로 옮긴다. 제목만 비우고 폴더·중요·즉시는 남겨
   * 같은 성격의 할 일을 연달아 적을 수 있게 한다.
   */
  const stageTitle = () => {
    const trimmed = title.trim()
    if (!trimmed || isSubmitting) return

    draftSeq.current += 1
    setStaged((current) => [...current, {
      kind: 'new',
      key: `new-${draftSeq.current}`,
      title: trimmed,
      folderId: folderId ? Number(folderId) : null,
      priority: selectedPriority === '중요',
      urgent: selectedUrgent,
    }])
    setTitle('')
    setTaskSubmitError(null)
    titleInputRef.current?.focus()
  }

  const toggleTask = (task: TaskSummaryResponse) => {
    setTaskSubmitError(null)
    setStaged((current) => {
      const key = existingKey(task.id)
      if (current.some((entry) => entry.key === key)) {
        return current.filter((entry) => entry.key !== key)
      }
      return [...current, {
        kind: 'existing',
        key,
        taskId: task.id,
        title: task.title,
        folderName: activeFolderId === null ? '' : folderNameById.get(activeFolderId) ?? '',
        priority: task.priority,
        urgent: task.urgent,
      }]
    })
  }

  const unstage = (key: string) => {
    setTaskSubmitError(null)
    setStaged((current) => current.filter((entry) => entry.key !== key))
  }

  const selectAddMode = (mode: AddMode) => {
    setAddMode(mode)
    setTaskSubmitError(null)
  }

  const addAllTasks = async () => {
    if (staged.length === 0 || isSubmitting) return

    setIsSubmitting(true)
    setTaskSubmitError(null)

    try {
      await onAddTasks({
        existingTaskIds: staged.flatMap((entry) => (entry.kind === 'existing' ? [entry.taskId] : [])),
        newTasks: staged.flatMap((entry) => (entry.kind === 'new'
          ? [{ title: entry.title, folderId: entry.folderId, priority: entry.priority, urgent: entry.urgent }]
          : [])),
        planDate: planDate || null,
      })
      dialogRef.current?.close()
    } catch {
      setTaskSubmitError('할 일을 추가하지 못했습니다. 아무것도 만들어지지 않았으니 다시 시도해 주세요.')
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
            <p>왼쪽에서 담고 오른쪽에서 확인한 뒤 한 번에 추가합니다.</p>
          </div>
          <button type="button" aria-label="Task 선택 창 닫기" disabled={isSubmitting} onClick={requestClose}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.panes}>
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
            <section className={styles['planning-option-group']} aria-labelledby="task-picker-option-label">
              <span className={styles['planning-option-label']} id="task-picker-option-label">선택</span>
              <div className={styles['planning-option-field']}>
                <label className={styles['date-option']}>
                  <input aria-label="캘린더 날짜" type="date" value={planDate} onChange={(event) => setPlanDate(event.target.value)} disabled={isSubmitting} />
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
              </div>
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
              <div className={styles['quick-add-row']}>
                <input
                  id="daily-plan-ad-hoc-title"
                  ref={titleInputRef}
                  value={title}
                  maxLength={255}
                  placeholder="할 일을 입력해 주세요"
                  disabled={isSubmitting}
                  onChange={(event) => {
                    setTitle(event.target.value)
                    setTaskSubmitError(null)
                  }}
                  onKeyDown={(event) => {
                    // 모달의 submit은 "모두 추가"라서, Enter로 저장되지 않게 막고 담기로 돌린다.
                    if (event.key !== 'Enter') return
                    event.preventDefault()
                    // 한글 조합을 끝내는 Enter는 keydown이 한 번 더 온다. 조합 중에는 담지 않아야
                    // 조합 전 글자('ㅇ')와 완성된 글자('ㅇㅇ')가 따로 담기는 일이 없다.
                    if (event.nativeEvent.isComposing) return
                    stageTitle()
                  }}
                />
                <ActionButton
                  variant="outline"
                  className={styles['stage-action']}
                  icon={<IconPlus size={16} aria-hidden="true" />}
                  disabled={!canStageTitle || isSubmitting}
                  onClick={stageTitle}
                >
                  담기
                </ActionButton>
              </div>
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
                ) : !activeProject || activeProject.items.length === 0 ? (
                  <p className={styles.state}>이 폴더에는 선택할 할 일이 없습니다.</p>
                ) : (
                  <div className={styles.group}>
                    <ul>
                      {activeProject.items.map((task) => {
                        const alreadyAdded = selectedTaskIds.has(task.id)
                        const pending = stagedExistingIds.has(task.id)
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
                              aria-label={`${task.title} ${pending ? '담기 취소' : '담기'}`}
                              disabled={alreadyAdded || isSubmitting}
                              onClick={() => toggleTask(task)}
                            >
                              {pending
                                ? <IconCheck size={16} aria-hidden="true" />
                                : !alreadyAdded && <IconPlus size={16} aria-hidden="true" />}
                              {alreadyAdded ? '추가됨' : ''}
                            </button>
                          </li>
                        )
                      })}
                    </ul>
                    {activeProject.hasNext && (
                      <LoadMoreButton
                        className={styles['load-more']}
                        isLoading={isLoadingMoreTasks}
                        disabled={isSubmitting}
                        onClick={() => void loadMoreTasks()}
                      />
                    )}
                  </div>
                )}
              </section>
            </section>
          </div>

          <section className={styles.stage} aria-labelledby="staged-tasks-title">
            <div className={styles['stage-heading']}>
              <h3 id="staged-tasks-title">추가될 할 일</h3>
              <span>{staged.length}개</span>
            </div>
            {staged.length === 0 ? (
              <p className={styles['stage-empty']}>왼쪽에서 담은 할 일이 여기에 모입니다.</p>
            ) : (
              <ul>
                {staged.map((entry) => (
                  <li key={entry.key}>
                    <span>
                      <small>
                        {entry.kind === 'new'
                          ? (entry.folderId === null ? '미분류' : folderNameById.get(entry.folderId) ?? '미분류')
                          : entry.folderName || '미분류'}
                      </small>
                      <strong>
                        {(entry.priority || entry.urgent) && (
                          <span aria-label={`${entry.urgent ? '즉시 ' : ''}${entry.priority ? '중요' : ''}`}>
                            {entry.urgent ? '⚡' : ''}{entry.priority ? '📌' : ''}{' '}
                          </span>
                        )}
                        {entry.title}
                      </strong>
                    </span>
                    <button
                      type="button"
                      aria-label={`${entry.title} 빼기`}
                      disabled={isSubmitting}
                      onClick={() => unstage(entry.key)}
                    >
                      <IconX size={16} aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <footer className={styles.footer}>
          {taskSubmitError && <p className={styles.error} role="alert">{taskSubmitError}</p>}
          <ActionButton
            type="submit"
            className={styles['submit-action']}
            isLoading={isSubmitting}
            loadingLabel="추가 중…"
            disabled={staged.length === 0}
          >
            {staged.length === 0 ? '할 일 추가' : `할 일 ${staged.length}개 추가`}
          </ActionButton>
        </footer>
      </form>
    </dialog>
  )
}
