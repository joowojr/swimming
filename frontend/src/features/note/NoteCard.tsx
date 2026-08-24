import { useEffect, useRef, useState } from 'react'
import {
  IconArchive,
  IconCalendar,
  IconMinus,
  IconPlus,
  IconSparkles,
  IconTrash,
} from '@tabler/icons-react'
import InlineEditableText from '../../components/InlineEditableText'
import {
  archiveNote,
  createNote,
  deleteNote,
  getNote,
  getNotes,
  restoreNote,
  updateNote,
} from './noteApi'
import type { NoteResponse } from './noteTypes'
import { addDailyPlanItems } from '../plans/dailyPlanApi'
import {
  confirmTaskOrganization,
  previewTaskOrganization,
} from './taskOrganizerApi'
import type { TaskOrganizeResponse } from './taskOrganizerTypes'
import styles from './NoteCard.module.css'

interface ProjectOption {
  id: number
  name: string
}

interface NoteCardProps {
  projects: ProjectOption[]
}

interface PreviewTaskItem {
  id: string
  sourceText: string
  title: string
  projectId: number | null
  projectName: string | null
  selected: boolean
}

interface PlanLinkItem {
  id: number
  title: string
  projectName: string | null
  planDate: string
  selected: boolean
}

type LoadStatus = 'loading' | 'ready' | 'error'
type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

const AUTO_SAVE_DELAY_MS = 700
const ORGANIZE_LOADING_MESSAGE_INTERVAL_MS = 1000
const ORGANIZE_LOADING_MESSAGES = [
  '최근 프로젝트 목록을 조회하고 있어요',
  '최근 할 일 목록을 살펴보고 있어요',
  '메모에서 할 일을 정리하고 있어요',
  '정리 결과를 준비하고 있어요',
] as const
const noteDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
})

function getNotePreview(content: string | undefined) {
  return content?.split(/\r?\n/, 1)[0].trim() || '메모'
}

function formatNoteDate(createdAt: string | undefined) {
  if (!createdAt) return ''
  const createdDate = new Date(createdAt)
  if (Number.isNaN(createdDate.getTime())) return ''
  return noteDateFormatter.format(createdDate)
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatPlanDate(planDate: string) {
  const [, month, day] = planDate.split('-')
  return month && day ? `${month}/${day}` : planDate
}

function toPreviewItems(preview: TaskOrganizeResponse): PreviewTaskItem[] {
  return [
    ...preview.suggestions.map((suggestion, index) => ({
      id: `suggestion-${index}`,
      sourceText: suggestion.sourceText,
      title: suggestion.title,
      projectId: suggestion.projectId,
      projectName: suggestion.projectName,
      selected: true,
    })),
    ...preview.unclassified.map((item, index) => ({
      id: `unclassified-${index}`,
      sourceText: item.sourceText,
      title: item.title,
      projectId: null,
      projectName: null,
      selected: true,
    })),
  ]
}

export default function NoteCard({ projects }: NoteCardProps) {
  const [memo, setMemo] = useState('')
  const [notes, setNotes] = useState<NoteResponse[]>([])
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null)
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading')
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle')
  const [isOrganizing, setIsOrganizing] = useState(false)
  const [organizeLoadingMessageIndex, setOrganizeLoadingMessageIndex] = useState(0)
  const [isConfirmingTasks, setIsConfirmingTasks] = useState(false)
  const [previewNoteId, setPreviewNoteId] = useState<number | null>(null)
  const [previewItems, setPreviewItems] = useState<PreviewTaskItem[] | null>(null)
  const [previewMessage, setPreviewMessage] = useState<string | null>(null)
  const [editingProjectItemId, setEditingProjectItemId] = useState<string | null>(null)
  const [planLinkItems, setPlanLinkItems] = useState<PlanLinkItem[] | null>(null)
  const [editingPlanDateTaskId, setEditingPlanDateTaskId] = useState<number | null>(null)
  const [isLinkingPlan, setIsLinkingPlan] = useState(false)
  const [planLinkMessage, setPlanLinkMessage] = useState<string | null>(null)
  const [isStartingNew, setIsStartingNew] = useState(false)
  const [isSelectingNote, setIsSelectingNote] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false)
  const [recentlyArchivedId, setRecentlyArchivedId] = useState<number | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)

  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const planDateInputRef = useRef<HTMLInputElement>(null)
  const memoRef = useRef('')
  const noteIdRef = useRef<number | null>(null)
  const lastSavedContentRef = useRef('')
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const saveQueueRef = useRef<Promise<void>>(Promise.resolve())
  const pendingContentRef = useRef<string | null>(null)
  const pendingSaveRef = useRef<Promise<boolean> | null>(null)
  const isMountedRef = useRef(true)

  useEffect(() => {
    isMountedRef.current = true
    let cancelled = false

    async function loadLatestNote() {
      try {
        const notes = await getNotes({ contextType: 'DEFAULT' })
        if (cancelled) return

        const latestNote = notes[0]
        const content = latestNote?.content ?? ''

        noteIdRef.current = latestNote?.id ?? null
        memoRef.current = content
        lastSavedContentRef.current = content
        setNotes(notes)
        setSelectedNoteId(latestNote?.id ?? null)
        setMemo(content)
        setLoadStatus('ready')
        setSaveStatus(latestNote ? 'saved' : 'idle')
      } catch {
        if (cancelled) return
        setLoadStatus('error')
      }
    }

    void loadLatestNote()

    return () => {
      cancelled = true
      isMountedRef.current = false
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
    }
  }, [])

  useEffect(() => {
    if (!isOrganizing || previewItems !== null) return

    const messageTimer = window.setInterval(() => {
      setOrganizeLoadingMessageIndex((currentIndex) =>
        Math.min(currentIndex + 1, ORGANIZE_LOADING_MESSAGES.length - 1),
      )
    }, ORGANIZE_LOADING_MESSAGE_INTERVAL_MS)

    return () => window.clearInterval(messageTimer)
  }, [isOrganizing, previewItems])

  useEffect(() => {
    if (editingPlanDateTaskId === null) return

    const animationFrame = window.requestAnimationFrame(() => {
      const input = planDateInputRef.current
      if (!input) return
      input.focus()
      try {
        input.showPicker()
      } catch {
        // 브라우저가 프로그래밍 방식의 date picker 열기를 지원하지 않으면 포커스만 유지한다.
      }
    })

    return () => window.cancelAnimationFrame(animationFrame)
  }, [editingPlanDateTaskId])

  const clearSaveTimer = () => {
    if (!saveTimerRef.current) return
    clearTimeout(saveTimerRef.current)
    saveTimerRef.current = null
  }

  const saveContent = (content: string): Promise<boolean> => {
    clearSaveTimer()

    if (!content.trim() || content === lastSavedContentRef.current) {
      return Promise.resolve(true)
    }

    if (content === pendingContentRef.current && pendingSaveRef.current) {
      return pendingSaveRef.current
    }

    pendingContentRef.current = content

    const request = saveQueueRef.current.then(async () => {
      if (content === lastSavedContentRef.current) return true
      if (isMountedRef.current) setSaveStatus('saving')

      try {
        let savedNote: NoteResponse

        if (noteIdRef.current === null) {
          const createdNote = await createNote({
            content,
            contextType: 'DEFAULT',
            projectId: null,
            sessionId: null,
          })
          savedNote = await getNote(createdNote.id)
        } else {
          savedNote = await updateNote(noteIdRef.current, { content })
        }

        noteIdRef.current = savedNote.id
        lastSavedContentRef.current = savedNote.content

        if (isMountedRef.current) {
          setNotes((currentNotes) => {
            const noteExists = currentNotes.some(
              (note) => note.id === savedNote.id,
            )

            if (!noteExists) return [savedNote, ...currentNotes]
            return currentNotes.map((note) =>
              note.id === savedNote.id ? savedNote : note,
            )
          })
          setSelectedNoteId(savedNote.id)
          setSaveStatus(
            memoRef.current === savedNote.content ? 'saved' : 'idle',
          )
        }
        return true
      } catch {
        if (isMountedRef.current) setSaveStatus('error')
        return false
      }
    })

    saveQueueRef.current = request.then(() => undefined)

    const exposedRequest = request.finally(() => {
      if (pendingSaveRef.current !== exposedRequest) return
      pendingContentRef.current = null
      pendingSaveRef.current = null
    })
    pendingSaveRef.current = exposedRequest

    return exposedRequest
  }

  const scheduleSave = (content: string) => {
    clearSaveTimer()

    if (!content.trim()) {
      setSaveStatus('idle')
      return
    }

    if (content === lastSavedContentRef.current) {
      setSaveStatus('saved')
      return
    }

    setSaveStatus('idle')
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null
      void saveContent(content)
    }, AUTO_SAVE_DELAY_MS)
  }

  const resetToNewDraft = () => {
    clearSaveTimer()
    noteIdRef.current = null
    memoRef.current = ''
    lastSavedContentRef.current = ''
    setSelectedNoteId(null)
    setMemo('')
    setSaveStatus('idle')
    setIsConfirmingDelete(false)
    setRecentlyArchivedId(null)
    setActionMessage(null)
    setPreviewNoteId(null)
    setPreviewItems(null)
    setPreviewMessage(null)
    setEditingProjectItemId(null)
    setPlanLinkItems(null)
    setEditingPlanDateTaskId(null)
    setPlanLinkMessage(null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const openNote = (note: NoteResponse) => {
    clearSaveTimer()
    noteIdRef.current = note.id
    memoRef.current = note.content
    lastSavedContentRef.current = note.content
    setSelectedNoteId(note.id)
    setMemo(note.content)
    setSaveStatus('saved')
    setIsConfirmingDelete(false)
    setRecentlyArchivedId(null)
    setActionMessage(null)
    setPreviewNoteId(null)
    setPreviewItems(null)
    setPreviewMessage(null)
    setEditingProjectItemId(null)
    setPlanLinkItems(null)
    setEditingPlanDateTaskId(null)
    setPlanLinkMessage(null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const removeNoteFromList = (noteId: number) => {
    const remainingNotes = notes.filter((note) => note.id !== noteId)
    setNotes(remainingNotes)

    if (noteIdRef.current !== noteId) return

    const nextNote = remainingNotes[0]
    if (nextNote) {
      openNote(nextNote)
      return
    }

    resetToNewDraft()
  }

  const handleMemoChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    const content = event.target.value
    setActionMessage(null)
    memoRef.current = content
    setMemo(content)
    scheduleSave(content)
  }

  const handleBlur = () => {
    void saveContent(memoRef.current)
  }

  const handleNewMemo = async () => {
    if (
      isStartingNew ||
      isSelectingNote ||
      isOrganizing ||
      isArchiving ||
      isDeleting
    ) return
    setIsStartingNew(true)

    const saved = await saveContent(memoRef.current)
    if (saved) resetToNewDraft()

    if (isMountedRef.current) setIsStartingNew(false)
  }

  const handleSelectNote = async (note: NoteResponse) => {
    if (
      note.id === noteIdRef.current ||
      isSelectingNote ||
      isStartingNew ||
      isOrganizing ||
      isArchiving ||
      isDeleting
    ) return
    setIsSelectingNote(true)

    const saved = await saveContent(memoRef.current)
    if (saved) {
      openNote(note)
    }

    if (isMountedRef.current) setIsSelectingNote(false)
  }

  const handleOrganize = async () => {
    if (
      !memo.trim() ||
      isOrganizing ||
      isStartingNew ||
      isSelectingNote ||
      isArchiving ||
      isDeleting
    ) return
    setOrganizeLoadingMessageIndex(0)
    setIsOrganizing(true)
    setActionMessage(null)

    try {
      const saved = await saveContent(memoRef.current)
      if (!saved) return
      const preview = await previewTaskOrganization({ memo: memoRef.current })
      setPreviewNoteId(noteIdRef.current)
      setPreviewItems(toPreviewItems(preview))
      setPreviewMessage(null)
    } catch {
      setActionMessage('할 일을 정리하지 못했어요')
    } finally {
      if (isMountedRef.current) setIsOrganizing(false)
    }
  }

  const selectedPreviewItems = previewItems?.filter(
    (item) => item.selected,
  ) ?? []
  const hasUntitledSelectedItem = selectedPreviewItems.some(
    (item) => !item.title.trim(),
  )

  const updatePreviewItem = (
    itemId: string,
    update: (item: PreviewTaskItem) => PreviewTaskItem,
  ) => {
    setPreviewItems((currentItems) =>
      currentItems?.map((item) =>
        item.id === itemId ? update(item) : item,
      ) ?? null,
    )
    setPreviewMessage(null)
  }

  const handlePreviewProjectChange = (itemId: string, value: string) => {
    const projectId = value ? Number(value) : null
    const projectName = projectId === null
      ? null
      : projects.find((project) => project.id === projectId)?.name ?? null

    updatePreviewItem(itemId, (item) => ({
      ...item,
      projectId,
      projectName,
    }))
    setEditingProjectItemId(null)
  }

  const handleCancelPreview = () => {
    setPreviewNoteId(null)
    setPreviewItems(null)
    setPreviewMessage(null)
    setEditingProjectItemId(null)
    setActionMessage(null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const finishPlanLink = (message: string) => {
    setPreviewNoteId(null)
    setPreviewItems(null)
    setPreviewMessage(null)
    setEditingProjectItemId(null)
    setPlanLinkItems(null)
    setEditingPlanDateTaskId(null)
    setPlanLinkMessage(null)
    setActionMessage(message)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const handleConfirmTasks = async () => {
    if (
      previewNoteId === null ||
      selectedPreviewItems.length === 0 ||
      hasUntitledSelectedItem ||
      isConfirmingTasks
    ) return

    setIsConfirmingTasks(true)
    setPreviewMessage(null)

    try {
      const response = await confirmTaskOrganization({
        noteId: previewNoteId,
        tasks: selectedPreviewItems.map((item) => ({
          sourceText: item.sourceText,
          projectId: item.projectId,
          title: item.title.trim(),
        })),
      })
      const today = formatLocalDate(new Date())
      setPlanLinkItems(response.createdTasks.map((task) => ({
        id: task.id,
        title: task.title,
        projectName: task.projectId === null
          ? null
          : projects.find((project) => project.id === task.projectId)?.name ?? '프로젝트',
        planDate: today,
        selected: true,
      })))
      setPreviewItems(null)
      setPreviewMessage(null)
      setEditingProjectItemId(null)
      setPlanLinkMessage(null)
    } catch {
      setPreviewMessage('할 일을 만들지 못했어요. 선택 내용을 그대로 유지했어요.')
    } finally {
      if (isMountedRef.current) setIsConfirmingTasks(false)
    }
  }

  const updatePlanLinkItem = (
    taskId: number,
    update: (item: PlanLinkItem) => PlanLinkItem,
  ) => {
    setPlanLinkItems((currentItems) =>
      currentItems?.map((item) => item.id === taskId ? update(item) : item) ?? null,
    )
    setPlanLinkMessage(null)
  }

  const handleLinkPlan = async () => {
    if (planLinkItems === null || isLinkingPlan) return

    const selectedItems = planLinkItems.filter((item) => item.selected)
    if (selectedItems.length === 0) return

    const taskIdsByDate = new Map<string, number[]>()
    selectedItems.forEach((item) => {
      const taskIds = taskIdsByDate.get(item.planDate) ?? []
      taskIds.push(item.id)
      taskIdsByDate.set(item.planDate, taskIds)
    })

    setIsLinkingPlan(true)
    setPlanLinkMessage(null)
    let remainingItems = planLinkItems
    let linkedCount = 0

    try {
      for (const [planDate, taskIds] of taskIdsByDate) {
        await addDailyPlanItems(planDate, { taskIds })
        const linkedTaskIds = new Set(taskIds)
        remainingItems = remainingItems.filter((item) => !linkedTaskIds.has(item.id))
        linkedCount += taskIds.length
        setPlanLinkItems(remainingItems)
      }

      finishPlanLink(`${linkedCount}개 할 일을 계획에 연결했어요`)
    } catch {
      setPlanLinkItems(remainingItems)
      setPlanLinkMessage(
        linkedCount > 0
          ? `${linkedCount}개는 연결했어요. 남은 할 일을 다시 연결해 주세요.`
          : '계획에 연결하지 못했어요. 날짜와 선택 내용을 그대로 유지했어요.',
      )
    } finally {
      if (isMountedRef.current) setIsLinkingPlan(false)
    }
  }

  const handleArchive = async () => {
    const noteId = noteIdRef.current
    if (noteId === null || isEditorDisabled) return
    setIsArchiving(true)
    setActionMessage(null)

    try {
      const saved = await saveContent(memoRef.current)
      if (!saved) return

      await archiveNote(noteId)
      removeNoteFromList(noteId)
      setRecentlyArchivedId(noteId)
    } catch {
      setActionMessage('메모를 보관하지 못했어요')
    } finally {
      if (isMountedRef.current) setIsArchiving(false)
    }
  }

  const handleRestoreRecent = async () => {
    const noteId = recentlyArchivedId
    if (noteId === null || isArchiving) return
    setIsArchiving(true)
    setActionMessage(null)

    try {
      const saved = await saveContent(memoRef.current)
      if (!saved) return

      await restoreNote(noteId)
      const restoredNote = await getNote(noteId)
      setNotes((currentNotes) => [
        restoredNote,
        ...currentNotes.filter((note) => note.id !== restoredNote.id),
      ])
      openNote(restoredNote)
    } catch {
      setActionMessage('메모를 복원하지 못했어요')
    } finally {
      if (isMountedRef.current) setIsArchiving(false)
    }
  }

  const handleDelete = async () => {
    const noteId = noteIdRef.current
    if (noteId === null || isDeleting) return
    setIsDeleting(true)
    setActionMessage(null)

    try {
      await saveContent(memoRef.current)
      await deleteNote(noteId)
      removeNoteFromList(noteId)
      setRecentlyArchivedId(null)
      setIsConfirmingDelete(false)
    } catch {
      setActionMessage('메모를 삭제하지 못했어요')
    } finally {
      if (isMountedRef.current) setIsDeleting(false)
    }
  }

  const statusMessage = (() => {
    if (loadStatus === 'loading') return '메모를 불러오는 중…'
    if (loadStatus === 'error') return '메모를 불러오지 못했어요'
    if (actionMessage) return actionMessage
    if (!memo.trim()) return '입력하면 자동으로 저장돼요'
    if (saveStatus === 'saving') return '저장 중…'
    if (saveStatus === 'saved') return '저장됨'
    if (saveStatus === 'error') return '저장하지 못했어요. 다시 입력하면 재시도해요'
    return '입력을 멈추면 자동으로 저장돼요'
  })()

  const isEditorDisabled =
    loadStatus !== 'ready' ||
    isOrganizing ||
    isConfirmingTasks ||
    planLinkItems !== null ||
    isLinkingPlan ||
    isStartingNew ||
    isSelectingNote ||
    isArchiving ||
    isDeleting

  return (
    <section className={styles['memo-card']} aria-labelledby="memo-title">
      {!isOrganizing && previewItems === null && planLinkItems === null && (
        <div className={styles['memo-head']}>
          <h3 id="memo-title" className={styles['memo-title']}>메모</h3>
          <div className={styles['memo-head-actions']}>
          <button
            type="button"
            className={styles['memo-icon-action']}
            onClick={handleArchive}
            disabled={selectedNoteId === null || isEditorDisabled}
            aria-label="현재 메모 보관"
            title="메모 보관"
          >
            <IconArchive size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            className={styles['memo-icon-action']}
            onClick={() => setIsConfirmingDelete(true)}
            disabled={selectedNoteId === null || isEditorDisabled}
            aria-label="현재 메모 삭제"
            aria-expanded={isConfirmingDelete}
            title="메모 삭제"
          >
            <IconTrash size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            className={styles['new-memo-action']}
            onClick={handleNewMemo}
            disabled={loadStatus !== 'ready' || isEditorDisabled}
          >
            <IconPlus size={16} aria-hidden="true" />
            새 메모
          </button>
          </div>
        </div>
      )}

      {previewItems === null && planLinkItems === null && isConfirmingDelete && (
        <div className={styles['delete-confirmation']} role="group" aria-label="메모 삭제 확인">
          <span>이 메모를 삭제할까요?</span>
          <div>
            <button
              type="button"
              onClick={() => setIsConfirmingDelete(false)}
              disabled={isDeleting}
            >
              취소
            </button>
            <button type="button" onClick={handleDelete} disabled={isDeleting}>
              {isDeleting ? '삭제 중…' : '삭제'}
            </button>
          </div>
        </div>
      )}

      {isOrganizing && previewItems === null && planLinkItems === null ? (
        <section
          className={styles['organize-loading']}
          aria-labelledby="organize-loading-title"
          aria-busy="true"
        >
          <div className={styles['organize-loading-heading']}>
            <h4 id="organize-loading-title">메모를 할 일로 정리하고 있어요</h4>
            <p
              className={styles['organize-loading-message']}
              key={organizeLoadingMessageIndex}
              role="status"
              aria-live="polite"
            >
              {ORGANIZE_LOADING_MESSAGES[organizeLoadingMessageIndex]}
            </p>
          </div>
          <ul className={styles['organize-loading-list']} aria-hidden="true">
            {[0, 1, 2].map((index) => (
              <li className={styles['organize-loading-row']} key={index}>
                <span className={styles['organize-loading-title']} />
                <span className={styles['organize-loading-project']} />
              </li>
            ))}
          </ul>
        </section>
      ) : planLinkItems !== null ? (
        <section
          className={styles['organize-preview']}
          aria-labelledby="plan-link-title"
          aria-busy={isLinkingPlan}
        >
          <div className={styles['organize-preview-heading']}>
            <h4 id="plan-link-title">계획에 연결하기</h4>
            <span>{planLinkItems.filter((item) => item.selected).length}개 선택</span>
          </div>

          <p className={styles['organize-preview-guide']}>
            만든 할 일을 진행할 날짜에 연결해 두세요.
          </p>
          <p className={styles['organize-preview-guide']}>
            날짜를 두 번 누르면 변경할 수 있어요.
          </p>

          <div className={styles['organize-preview-list-wrap']}>
            <ul className={styles['organize-task-list']}>
              {planLinkItems.map((item) => (
                <li
                  className={item.selected
                    ? styles['plan-link-task-row']
                    : styles['organize-task-row-excluded']}
                  key={item.id}
                >
                  <div className={styles['plan-link-task-fields']}>
                    <div className={styles['plan-link-title-row']}>
                      <span className={styles['plan-link-task-title']}>{item.title}</span>
                      {editingPlanDateTaskId === item.id ? (
                        <input
                          ref={planDateInputRef}
                          className={styles['plan-link-date-input']}
                          type="date"
                          value={item.planDate}
                          required
                          disabled={isLinkingPlan}
                          aria-label={`${item.title} 계획 날짜`}
                          onChange={(event) => updatePlanLinkItem(item.id, (currentItem) => ({
                            ...currentItem,
                            planDate: event.target.value || currentItem.planDate,
                          }))}
                          onBlur={() => setEditingPlanDateTaskId(null)}
                          onKeyDown={(event) => {
                            if (event.key === 'Escape' || event.key === 'Enter') {
                              setEditingPlanDateTaskId(null)
                            }
                          }}
                        />
                      ) : (
                        <button
                          type="button"
                          className={styles['plan-link-date-action']}
                          disabled={isLinkingPlan || !item.selected}
                          aria-label={`${item.title} 계획 날짜 ${formatPlanDate(item.planDate)}. 두 번 눌러 변경`}
                          title="두 번 눌러 날짜 변경"
                          onDoubleClick={() => setEditingPlanDateTaskId(item.id)}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault()
                              setEditingPlanDateTaskId(item.id)
                            }
                          }}
                        >
                          <IconCalendar size={14} aria-hidden="true" />
                          {formatPlanDate(item.planDate)}
                        </button>
                      )}
                    </div>
                    <span className={styles['plan-link-project-name']}>
                      {item.projectName ?? '미분류'}
                    </span>
                  </div>

                  <button
                    type="button"
                    className={styles['organize-exclude-action']}
                    disabled={isLinkingPlan}
                    onClick={() => updatePlanLinkItem(item.id, (currentItem) => ({
                      ...currentItem,
                      selected: !currentItem.selected,
                    }))}
                    aria-label={item.selected
                      ? `${item.title} 계획 연결에서 빼기`
                      : `${item.title} 계획 연결에 다시 포함`}
                    title={item.selected ? '연결에서 빼기' : '다시 포함'}
                  >
                    {item.selected
                      ? <IconMinus size={16} aria-hidden="true" />
                      : <IconPlus size={16} aria-hidden="true" />}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {planLinkMessage && (
            <p className={styles['organize-preview-message']} role="status">
              {planLinkMessage}
            </p>
          )}

          <div className={styles['organize-preview-actions']}>
            <button
              type="button"
              className={styles['organize-confirm-action']}
              disabled={
                planLinkItems.every((item) => !item.selected) ||
                isLinkingPlan
              }
              onClick={handleLinkPlan}
            >
              {isLinkingPlan
                ? '계획에 연결하는 중'
                : `${planLinkItems.filter((item) => item.selected).length}개 계획에 연결하기`}
            </button>
            <button
              type="button"
              className={styles['organize-cancel-action']}
              disabled={isLinkingPlan}
              onClick={() => finishPlanLink('할 일을 만들었어요')}
            >
              나중에
            </button>
          </div>
        </section>
      ) : previewItems === null ? (
        <>
          <textarea
            ref={textareaRef}
            className={styles['memo-paper']}
            placeholder="떠오르는 일을 편하게 적어두세요."
            value={memo}
            onChange={handleMemoChange}
            onBlur={handleBlur}
            disabled={isEditorDisabled}
          />

          <div className={styles['memo-foot']}>
            <span className={styles['memo-hint']} role="status" aria-live="polite">
              {statusMessage}
            </span>
            <button
              type="button"
              className={styles['organize-action']}
              onClick={handleOrganize}
              disabled={!memo.trim() || isEditorDisabled}
            >
              <IconSparkles size={16} aria-hidden="true" />
              {isOrganizing ? '정리하는 중' : '할 일로 정리'}
            </button>
          </div>

          {recentlyArchivedId !== null && (
            <div className={styles['archive-undo']} role="status">
              <span>메모를 보관했어요</span>
              <button
                type="button"
                onClick={handleRestoreRecent}
                disabled={isArchiving}
              >
                실행 취소
              </button>
            </div>
          )}

          <section
            className={styles['memo-list']}
            aria-labelledby="saved-memos-title"
          >
            <div className={styles['memo-list-head']}>
              <h4 id="saved-memos-title">메모 목록</h4>
              <span>{notes.length}개</span>
            </div>

            {notes.length > 0 ? (
              <ul className={styles['memo-list-items']}>
                {notes.map((note) => (
                  <li key={note.id}>
                    <button
                      type="button"
                      className={
                        selectedNoteId === note.id
                          ? styles['memo-list-action-active']
                          : styles['memo-list-action']
                      }
                      onClick={() => handleSelectNote(note)}
                      disabled={isEditorDisabled}
                      aria-pressed={selectedNoteId === note.id}
                    >
                      <span>{getNotePreview(note.content)}</span>
                      <time dateTime={note.createdAt}>
                        {formatNoteDate(note.createdAt)}
                      </time>
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className={styles['memo-list-empty']}>
                저장된 메모가 여기에 모여요.
              </p>
            )}
          </section>
        </>
      ) : (
          <section
              className={styles['organize-preview']}
              aria-labelledby="organize-preview-title"
          >
            <div className={styles['organize-preview-heading']}>
              <h4 id="organize-preview-title">할 일 미리보기</h4>
              <span>{selectedPreviewItems.length}개 등록</span>
            </div>

            <p className={styles['organize-preview-guide']}>
              할 일과 프로젝트를 클릭하면 원하는 대로
            </p>
            <p className={styles['organize-preview-guide']}>
              수정할 수 있어요
            </p>

            <div className={styles['organize-preview-list-wrap']}>
              {previewItems.length > 0 ? (
                  <ul className={styles['organize-task-list']}>
                    {previewItems.map((item) => {
                      const projectName = item.projectId === null
                          ? '미분류'
                          : projects.find((project) => project.id === item.projectId)?.name ??
                          item.projectName ??
                          '프로젝트'

                      return (
                          <li
                              className={
                                !item.selected
                                    ? styles['organize-task-row-excluded']
                                    : item.projectId === null
                                      ? styles['organize-task-row-unclassified']
                                      : styles['organize-task-row']
                              }
                              key={item.id}
                          >
                            <div className={styles['organize-task-fields']}>
                              <InlineEditableText
                                  className={styles['organize-title-edit']}
                                  errorClassName={styles['organize-title-error']}
                                  value={item.title}
                                  ariaLabel="Task 제목"
                                  maxLength={255}
                                  disabled={isConfirmingTasks}
                                  requiredMessage="Task 제목을 입력해 주세요."
                                  onSave={(title) => {
                                    updatePreviewItem(item.id, (currentItem) => ({
                                      ...currentItem,
                                      title,
                                    }))
                                    return Promise.resolve()
                                  }}
                              />

                              {editingProjectItemId === item.id ? (
                                  <select
                                      className={styles['organize-project-select']}
                                      value={item.projectId ?? ''}
                                      disabled={isConfirmingTasks}
                                      autoFocus
                                      onBlur={() => setEditingProjectItemId(null)}
                                      onChange={(event) =>
                                          handlePreviewProjectChange(item.id, event.target.value)
                                      }
                                      aria-label={`${item.title} Project 선택`}
                                  >
                                    <option value="">미분류</option>
                                    {projects.map((project) => (
                                        <option key={project.id} value={project.id}>
                                          {project.name}
                                        </option>
                                    ))}
                                  </select>
                              ) : (
                                  <button
                                      type="button"
                                      className={styles['organize-project-action']}
                                      disabled={isConfirmingTasks}
                                      onClick={() => setEditingProjectItemId(item.id)}
                                      aria-label={`${item.title} Project 변경`}
                                  >
                                    {projectName}
                                  </button>
                              )}
                            </div>

                            <button
                                type="button"
                                className={styles['organize-exclude-action']}
                                disabled={isConfirmingTasks}
                                onClick={() => updatePreviewItem(
                                    item.id,
                                    (currentItem) => ({
                                      ...currentItem,
                                      selected: !currentItem.selected,
                                    }),
                                )}
                                aria-label={item.selected
                                    ? `${item.title} 후보에서 빼기`
                                    : `${item.title} 다시 포함`}
                                title={item.selected ? '후보에서 빼기' : '다시 포함'}
                            >
                              {item.selected
                                  ? <IconMinus size={16} aria-hidden="true"/>
                                  : <IconPlus size={16} aria-hidden="true"/>}
                            </button>
                          </li>
                      )
                    })}
                  </ul>
              ) : (
                  <p className={styles['organize-preview-empty']}>
                    지금 만들 Task 후보는 없어요.
                  </p>
              )}
            </div>

            {previewMessage && (
                <p className={styles['organize-preview-message']} role="status">
                  {previewMessage}
                </p>
            )}

            <div className={styles['organize-preview-actions']}>
              <button
                  type="button"
                  className={styles['organize-confirm-action']}
                  onClick={handleConfirmTasks}
                  disabled={
                      selectedPreviewItems.length === 0 ||
                      hasUntitledSelectedItem ||
                      isConfirmingTasks
                  }
              >
                {isConfirmingTasks
                    ? '할 일 만드는 중'
                    : `${selectedPreviewItems.length}개 할 일 만들기`}
              </button>
              <button
                  type="button"
                  className={styles['organize-cancel-action']}
                  onClick={handleCancelPreview}
                  disabled={isConfirmingTasks}
              >
                취소
              </button>
            </div>
          </section>
      )}
    </section>
  )
}
