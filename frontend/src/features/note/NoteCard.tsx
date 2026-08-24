import { useEffect, useRef, useState } from 'react'
import {
  IconArchive,
  IconPlus,
  IconSparkles,
  IconTrash,
} from '@tabler/icons-react'
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
import styles from './NoteCard.module.css'

interface MemoCardProps {
  onOrganize: (text: string) => Promise<void> | void
}

type LoadStatus = 'loading' | 'ready' | 'error'
type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

const AUTO_SAVE_DELAY_MS = 700
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

export default function NoteCard({ onOrganize }: MemoCardProps) {
  const [memo, setMemo] = useState('')
  const [notes, setNotes] = useState<NoteResponse[]>([])
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null)
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading')
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle')
  const [isOrganizing, setIsOrganizing] = useState(false)
  const [isStartingNew, setIsStartingNew] = useState(false)
  const [isSelectingNote, setIsSelectingNote] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false)
  const [recentlyArchivedId, setRecentlyArchivedId] = useState<number | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)

  const textareaRef = useRef<HTMLTextAreaElement>(null)
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
    setIsOrganizing(true)

    try {
      const saved = await saveContent(memoRef.current)
      if (!saved) return
      await onOrganize(memoRef.current)
      resetToNewDraft()
    } finally {
      if (isMountedRef.current) setIsOrganizing(false)
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
    isStartingNew ||
    isSelectingNote ||
    isArchiving ||
    isDeleting

  return (
    <section className={styles['memo-card']} aria-labelledby="memo-title">
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

      {isConfirmingDelete && (
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
    </section>
  )
}
