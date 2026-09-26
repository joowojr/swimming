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
import { useDailyPlanStore } from '../../store/dailyPlanStore'
import { formatLocalDate, parseLocalDate } from '../../lib/date'
import { getDailyPlans } from './dailyPlanApi'
import type { DailyPlan, DailyPlanItem } from './dailyPlanTypes'
import styles from './TaskPickerModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'

/** 우측에 담아 둔 "새로 만들 할 일" 하나. 저장 전까지는 서버에 없다. */
export interface NewTaskDraft {
  title: string
  folderId: number | null
  priority: boolean
  urgent: boolean
}

/** 담은 이미 있는 할 일 하나. 속성을 적용하는 모달이면 priority·urgent는 담을 때 고른 값이다. */
export interface ExistingTaskPick {
  taskId: number
  title: string
  priority: boolean
  urgent: boolean
}

/**
 * "모두 추가"로 넘어가는 한 묶음.
 * existingTasks는 폴더·다른 날짜에서 고른 이미 있는 할 일, newTasks는 좌측에서 새로 적은 할 일이다.
 */
export interface TaskPickerSubmission {
  existingTasks: ExistingTaskPick[]
  newTasks: NewTaskDraft[]
  planDate: string | null
}

interface TaskPickerModalProps {
  selectedTaskIds?: ReadonlySet<number>
  /**
   * 이미 있는 할 일을 담을 날짜로 옮길 수 있는 모달이다. 다른 날짜에 담긴 할 일을 고르는 탭이 생기고,
   * 이미 있는 할 일을 하나라도 담으면 날짜가 필요하다(옮길 곳이 있어야 하므로).
   * 날짜를 모달에서 바꿀 수 있으므로, 이미 담긴 할 일은 selectedTaskIds 대신 고른 날짜의 캘린더로 판단한다.
   */
  canMoveFromOtherDates?: boolean
  /** 무엇을 담든 날짜가 꼭 있어야 한다. 캘린더처럼 날짜에 담는 것이 목적인 화면에서 켠다. */
  planDateRequired?: boolean
  /**
   * 속성(즉시·중요)을 이미 있는 할 일에도 적용한다. 모든 탭에 속성이 보이고, 담을 때 고른 값이 그 할 일의 값이 된다.
   * 매트릭스처럼 추가한 영역이 곧 속성인 화면에서 켠다. 끄면 이미 있는 할 일은 자기 속성을 그대로 둔다.
   */
  applyAttributesToExisting?: boolean
  /** 담은 것을 한 번에 저장한다. 실패하면 모달을 닫지 않고 담은 목록을 그대로 둔다. */
  onAddTasks: (submission: TaskPickerSubmission) => Promise<void>
  onClose: () => void
  initialPriority?: boolean
  initialUrgent?: boolean
  initialPlanDate?: string
}

/** 선택한 폴더의 상세를 불러오는 상태. idle은 아직 폴더를 고르지 않은 상태다. */
type DetailStatus = 'idle' | 'loading' | 'ready' | 'error'

type AddMode = 'direct' | 'folder' | 'other-dates'

/** 다른 날짜 탭의 목록을 불러오는 상태. idle은 아직 조회 기간을 다 고르지 않은 상태다. */
type OtherDatesStatus = 'idle' | 'loading' | 'ready' | 'error'

/** 한 번 불러온 조회 기간의 결과. 기간이 바뀌면 key가 달라져 다시 받는다. */
interface BrowseResult {
  key: string
  plans: DailyPlan[]
}

/**
 * 우측 목록의 한 줄. 새로 적은 것과 폴더에서 고른 것을 한 목록에서 보여주되,
 * 저장할 때는 서로 다른 경로로 나가므로 kind로 구분한다.
 */
type StagedTask =
  | { kind: 'new'; key: string; title: string; folderId: number | null; priority: boolean; urgent: boolean }
  | {
    kind: 'existing'
    key: string
    taskId: number
    title: string
    folderName: string
    priority: boolean
    urgent: boolean
    /** 다른 날짜 탭에서 담았으면 원래 담겨 있던 날짜다. */
    fromDate: string | null
  }

type StagedExistingTask = Extract<StagedTask, { kind: 'existing' }>

const ADD_MODE_OPTIONS = [
  {
    value: 'direct',
    label: '새 할 일',
    id: 'direct-add-tab',
    controls: 'direct-add-panel',
  },
  {
    value: 'folder',
    label: '폴더',
    id: 'folder-add-tab',
    controls: 'folder-add-panel',
  },
] as const

const OTHER_DATES_OPTION = {
  value: 'other-dates',
  label: '다른 날짜',
  id: 'other-dates-tab',
  controls: 'other-dates-panel',
} as const

const MOVABLE_ADD_MODE_OPTIONS = [...ADD_MODE_OPTIONS, OTHER_DATES_OPTION]

const NO_TASK_IDS: ReadonlySet<number> = new Set()

const monthDayFormatter = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
const fullDateFormatter = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' })

/** 올해 날짜는 연도를 빼고, 다른 해의 날짜만 연도를 붙인다. */
function formatPlanDate(date: string) {
  const parsed = parseLocalDate(date)
  return parsed.getFullYear() === new Date().getFullYear()
    ? monthDayFormatter.format(parsed)
    : fullDateFormatter.format(parsed)
}

/** 기준 날짜에서 days일 앞선 날짜. 기준 날짜가 없으면 빈 값이다. */
function daysBefore(date: string, days: number) {
  if (!date) return ''
  const shifted = parseLocalDate(date)
  shifted.setDate(shifted.getDate() - days)
  return formatLocalDate(shifted)
}

function stagedFromPlanItem(item: DailyPlanItem, fromDate: string): StagedExistingTask {
  return {
    kind: 'existing',
    key: existingKey(item.taskId),
    taskId: item.taskId,
    title: item.title,
    folderName: item.itemType === 'TASK' ? item.folderName : '',
    priority: item.priority,
    urgent: item.urgent,
    fromDate,
  }
}

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
  selectedTaskIds = NO_TASK_IDS,
  canMoveFromOtherDates = false,
  planDateRequired = false,
  applyAttributesToExisting = false,
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
  // 조회 기간은 기준 날짜 직전 일주일로 시작한다. 기준 날짜 자체는 옮길 날짜라 목록에서 빠지므로 넣지 않는다.
  // 담을 날짜가 비어 있으면(매트릭스) 오늘을 기준으로 한다.
  const browseBaseDate = initialPlanDate || formatLocalDate(new Date())
  const [browseFrom, setBrowseFrom] = useState(() => daysBefore(browseBaseDate, 7))
  const [browseTo, setBrowseTo] = useState(() => daysBefore(browseBaseDate, 1))
  const [browseResult, setBrowseResult] = useState<BrowseResult | null>(null)
  const [failedBrowseKey, setFailedBrowseKey] = useState<string | null>(null)
  const plannedOnDate = useDailyPlanStore((state) => (
    canMoveFromOtherDates && planDate ? state.entriesByDate[planDate] : undefined
  ))
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
  const isPlanDateNeeded = planDateRequired || (canMoveFromOtherDates && stagedExistingIds.size > 0)
  const alreadyAddedIds: ReadonlySet<number> = canMoveFromOtherDates
    ? new Set((plannedOnDate ?? []).map((entry) => entry.taskId))
    : selectedTaskIds
  const isBrowseRangeReversed = Boolean(browseFrom && browseTo && browseFrom > browseTo)
  const browseKey = browseFrom && browseTo && !isBrowseRangeReversed ? `${browseFrom}~${browseTo}` : null
  const otherDatesStatus: OtherDatesStatus = browseKey === null
    ? 'idle'
    : browseResult?.key === browseKey
      ? 'ready'
      : failedBrowseKey === browseKey
        ? 'error'
        : 'loading'
  // 옮길 날짜의 묶음과 끝낸 할 일은 옮길 대상이 아니므로 화면에서 뺀다. 옮길 날짜를 바꿔도 다시 받지 않는다.
  const otherDateGroups = browseResult?.key === browseKey
    ? browseResult.plans
      .filter((plan) => plan.date !== planDate)
      .map((plan) => ({ ...plan, items: plan.items.filter((item) => item.status !== 'DONE') }))
      .filter((plan) => plan.items.length > 0)
    : []

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

  useEffect(() => {
    if (addMode !== 'other-dates' || browseKey === null) return
    if (browseResult?.key === browseKey || failedBrowseKey === browseKey) return

    let active = true
    void getDailyPlans(browseFrom, browseTo)
      .then((plans) => {
        if (active) setBrowseResult({ key: browseKey, plans })
      })
      .catch(() => {
        if (active) setFailedBrowseKey(browseKey)
      })
    return () => { active = false }
  }, [addMode, browseKey, browseFrom, browseTo, browseResult, failedBrowseKey])

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

  /** 속성을 적용하는 모달이면 담는 순간의 즉시·중요를 입힌다. 새 할 일처럼 담은 뒤 칩을 바꿔도 이미 담은 줄은 그대로다. */
  const withPickedAttributes = (task: StagedExistingTask): StagedExistingTask => (applyAttributesToExisting
    ? { ...task, priority: selectedPriority === '중요', urgent: selectedUrgent }
    : task)

  const toggleExisting = (task: StagedExistingTask) => {
    setTaskSubmitError(null)
    setStaged((current) => (current.some((entry) => entry.key === task.key)
      ? current.filter((entry) => entry.key !== task.key)
      : [...current, withPickedAttributes(task)]))
  }

  const toggleTask = (task: TaskSummaryResponse) => {
    toggleExisting({
      kind: 'existing',
      key: existingKey(task.id),
      taskId: task.id,
      title: task.title,
      folderName: activeFolderId === null ? '' : folderNameById.get(activeFolderId) ?? '',
      priority: task.priority,
      urgent: task.urgent,
      fromDate: null,
    })
  }

  /** 그 날짜의 할 일을 한 번에 담는다. 이미 담은 것은 그대로 둔다. */
  const stageAllOn = (plan: DailyPlan) => {
    setTaskSubmitError(null)
    setStaged((current) => {
      const stagedKeys = new Set(current.map((entry) => entry.key))
      return [
        ...current,
        ...plan.items
          .map((item) => withPickedAttributes(stagedFromPlanItem(item, plan.date)))
          .filter((entry) => !stagedKeys.has(entry.key)),
      ]
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
        // 담은 뒤 날짜를 바꿔 이미 그 날짜에 있게 된 할 일은 옮길 필요가 없으므로 뺀다.
        // 속성을 적용하는 모달은 날짜가 같아도 즉시·중요를 바꿔야 하므로 빼지 않는다.
        existingTasks: staged.flatMap((entry) => (
          entry.kind === 'existing'
            && (applyAttributesToExisting || (entry.fromDate !== planDate && !alreadyAddedIds.has(entry.taskId)))
            ? [{ taskId: entry.taskId, title: entry.title, priority: entry.priority, urgent: entry.urgent }]
            : []
        )),
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
              options={canMoveFromOtherDates ? MOVABLE_ADD_MODE_OPTIONS : ADD_MODE_OPTIONS}
              value={addMode}
              disabled={isSubmitting}
              fullWidth
              semantics="tabs"
              onChange={selectAddMode}
            />
            <div className={styles['planning-options']}>
              <div className={styles['planning-option-group']}>
                <label className={styles['field-label']} htmlFor="task-picker-plan-date">담을 날짜</label>
                <div className={styles['date-option']}>
                  <input
                    id="task-picker-plan-date"
                    type="date"
                    value={planDate}
                    required={isPlanDateNeeded}
                    onChange={(event) => setPlanDate(event.target.value)}
                    disabled={isSubmitting}
                  />
                </div>
              </div>
              {(addMode === 'direct' || applyAttributesToExisting) && (
                <div className={styles['planning-option-group']} role="group" aria-labelledby="task-picker-attribute-label">
                  <span className={styles['field-label']} id="task-picker-attribute-label">속성</span>
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
                </div>
              )}
            </div>
            <section
              id="direct-add-panel"
              className={`${styles['mode-panel']} ${styles['quick-add']}`}
              role="tabpanel"
              aria-labelledby="direct-add-tab"
              hidden={addMode !== 'direct'}
            >
              <label className={styles['field-label']} htmlFor="daily-plan-ad-hoc-title">새 할 일</label>
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
                <label className={styles['field-label']} id="task-select-label" htmlFor="daily-plan-task-folder">폴더</label>
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
                        // 속성을 적용하는 모달은 이미 그 날짜에 있는 할 일도 즉시·중요를 바꾸려고 담을 수 있다.
                        const alreadyAdded = !applyAttributesToExisting && alreadyAddedIds.has(task.id)
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
            {canMoveFromOtherDates && (
              <section
                id="other-dates-panel"
                className={`${styles['mode-panel']} ${styles['other-dates']}`}
                role="tabpanel"
                aria-labelledby="other-dates-tab"
                hidden={addMode !== 'other-dates'}
              >
                <div className={styles['task-select']} role="group" aria-labelledby="browse-range-label">
                  <span className={styles['field-label']} id="browse-range-label">조회 기간</span>
                  <div className={styles['date-range']}>
                    <div className={styles['date-option']}>
                      <input
                        aria-label="조회 시작일"
                        type="date"
                        value={browseFrom}
                        max={browseTo || undefined}
                        onChange={(event) => setBrowseFrom(event.target.value)}
                        disabled={isSubmitting}
                      />
                    </div>
                    <span aria-hidden="true">~</span>
                    <div className={styles['date-option']}>
                      <input
                        aria-label="조회 종료일"
                        type="date"
                        value={browseTo}
                        min={browseFrom || undefined}
                        onChange={(event) => setBrowseTo(event.target.value)}
                        disabled={isSubmitting}
                      />
                    </div>
                  </div>
                </div>
                {isBrowseRangeReversed ? (
                  <p className={styles.state} role="alert">시작일을 종료일과 같거나 앞선 날짜로 골라 주세요.</p>
                ) : otherDatesStatus === 'idle' ? (
                  <p className={styles.state}>기간을 고르면 그동안 담긴 할 일을 확인할 수 있습니다.</p>
                ) : otherDatesStatus === 'error' ? (
                  <p className={styles.state} role="alert">할 일을 불러오지 못했습니다. 기간을 바꾸거나 잠시 후 다시 열어 주세요.</p>
                ) : otherDatesStatus === 'loading' ? (
                  <p className={styles.state} role="status">
                    <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
                    할 일을 불러오는 중…
                  </p>
                ) : otherDateGroups.length === 0 ? (
                  <p className={styles.state}>이 기간에 옮길 수 있는 할 일이 없습니다.</p>
                ) : otherDateGroups.map((plan) => {
                  const headingId = `other-date-${plan.date}`
                  const allStaged = plan.items.every((item) => stagedExistingIds.has(item.taskId))
                  return (
                    <section className={styles.group} aria-labelledby={headingId} key={plan.date}>
                      <div className={styles['group-heading']}>
                        <h3 id={headingId}>{formatPlanDate(plan.date)}</h3>
                        <button
                          type="button"
                          aria-label={`${formatPlanDate(plan.date)} 할 일 모두 담기`}
                          disabled={allStaged || isSubmitting}
                          onClick={() => stageAllOn(plan)}
                        >
                          {allStaged ? '모두 담음' : '모두 담기'}
                        </button>
                      </div>
                      <ul>
                        {plan.items.map((item) => {
                          const pending = stagedExistingIds.has(item.taskId)
                          return (
                            <li className={pending ? styles.selected : undefined} key={item.taskId}>
                              <span>
                                {(item.priority || item.urgent) && (
                                  <span aria-label={`${item.urgent ? '즉시 ' : ''}${item.priority ? '중요' : ''}`}>
                                    {item.urgent ? '⚡' : ''}{item.priority ? '📌' : ''}
                                  </span>
                                )} {item.title}
                              </span>
                              <button
                                type="button"
                                aria-pressed={pending}
                                aria-label={`${item.title} ${pending ? '담기 취소' : '담기'}`}
                                disabled={isSubmitting}
                                onClick={() => toggleExisting(stagedFromPlanItem(item, plan.date))}
                              >
                                {pending
                                  ? <IconCheck size={16} aria-hidden="true" />
                                  : <IconPlus size={16} aria-hidden="true" />}
                              </button>
                            </li>
                          )
                        })}
                      </ul>
                    </section>
                  )
                })}
              </section>
            )}
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
                        {entry.kind === 'existing' && entry.fromDate && ` · ${formatPlanDate(entry.fromDate)}에서`}
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
          {!planDateRequired && isPlanDateNeeded && !planDate && (
            <p className={styles['footer-hint']}>이미 있는 할 일을 옮기려면 담을 날짜를 골라 주세요.</p>
          )}
          <ActionButton
            type="submit"
            className={styles['submit-action']}
            isLoading={isSubmitting}
            loadingLabel="추가 중…"
            disabled={staged.length === 0 || (isPlanDateNeeded && !planDate)}
          >
            {staged.length === 0 ? '할 일 추가' : `할 일 ${staged.length}개 추가`}
          </ActionButton>
        </footer>
      </form>
    </dialog>
  )
}
