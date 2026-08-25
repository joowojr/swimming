import { useCallback, useEffect, useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import {
  archiveNote,
  createNote,
  deleteNote,
  getNote,
  getNotes,
  restoreNote,
  updateNote,
} from './noteApi'
import NoteEditor from './NoteEditor'
import NoteList from './NoteList'
import TaskOrganizerPanel from './TaskOrganizerPanel'
import type { NoteResponse } from './noteTypes'
import type { LoadStatus, ProjectOption, SaveStatus } from './noteViewTypes'
import styles from './NoteCard.module.css'

/**
 * 역할: 메모의 선택·자동 저장·보관·삭제를 관리하고, 각 화면 조각과 Task Organizer 흐름을 조합한다.
 * AI 정리와 계획 연결의 세부 상태는 TaskOrganizerPanel이 소유한다.
 */
interface NoteCardProps {
  projects: ProjectOption[]
  projectId?: number
  sessionId?: number
  className?: string
}

interface OrganizerSource {
  noteId: number
  memo: string
}

const AUTO_SAVE_DELAY_MS = 700

export default function NoteCard({ projects, projectId, sessionId, className }: NoteCardProps) {
  const [memo, setMemo] = useState('')
  const [notes, setNotes] = useState<NoteResponse[]>([])
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null)
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading')
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle')
  const [organizerSource, setOrganizerSource] = useState<OrganizerSource | null>(null)
  const [isStartingNew, setIsStartingNew] = useState(false)
  const [isSelectingNote, setIsSelectingNote] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false)
  const [recentlyArchivedId, setRecentlyArchivedId] = useState<number | null>(null)
  const [archiveSuggestionNoteId, setArchiveSuggestionNoteId] = useState<number | null>(null)
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
        const loadedNotes = sessionId !== undefined
          ? await getNotes({ sessionId })
          : projectId === undefined
            ? await getNotes({ contextType: 'DEFAULT' })
            : await getNotes({ projectId })
        if (cancelled) return

        const latestNote = loadedNotes[0]
        const content = latestNote?.content ?? ''
        noteIdRef.current = latestNote?.id ?? null
        memoRef.current = content
        lastSavedContentRef.current = content
        setNotes(loadedNotes)
        setSelectedNoteId(latestNote?.id ?? null)
        setMemo(content)
        setLoadStatus('ready')
        setSaveStatus(latestNote ? 'saved' : 'idle')
      } catch {
        if (!cancelled) setLoadStatus('error')
      }
    }

    void loadLatestNote()
    return () => {
      cancelled = true
      isMountedRef.current = false
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
    }
  }, [projectId, sessionId])

  const clearSaveTimer = () => {
    if (!saveTimerRef.current) return
    clearTimeout(saveTimerRef.current)
    saveTimerRef.current = null
  }

  const saveContent = (content: string): Promise<boolean> => {
    clearSaveTimer()
    if (!content.trim() || content === lastSavedContentRef.current) return Promise.resolve(true)
    if (content === pendingContentRef.current && pendingSaveRef.current) return pendingSaveRef.current

    pendingContentRef.current = content
    const request = saveQueueRef.current.then(async () => {
      if (content === lastSavedContentRef.current) return true
      if (isMountedRef.current) setSaveStatus('saving')

      try {
        const savedNote = noteIdRef.current === null
          ? await createNote(sessionId !== undefined
            ? { content, contextType: 'SESSION', projectId: null, sessionId }
            : projectId === undefined
              ? { content, contextType: 'DEFAULT', projectId: null, sessionId: null }
              : { content, contextType: 'PROJECT', projectId, sessionId: null })
              .then((createdNote) => getNote(createdNote.id))
          : await updateNote(noteIdRef.current, { content })

        noteIdRef.current = savedNote.id
        lastSavedContentRef.current = savedNote.content
        if (isMountedRef.current) {
          setNotes((currentNotes) => currentNotes.some((note) => note.id === savedNote.id)
            ? currentNotes.map((note) => note.id === savedNote.id ? savedNote : note)
            : [savedNote, ...currentNotes])
          setSelectedNoteId(savedNote.id)
          setSaveStatus(memoRef.current === savedNote.content ? 'saved' : 'idle')
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
    setArchiveSuggestionNoteId(null)
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
    setArchiveSuggestionNoteId(null)
    setActionMessage(null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const removeNoteFromList = (noteId: number) => {
    const remainingNotes = notes.filter((note) => note.id !== noteId)
    setNotes(remainingNotes)
    if (noteIdRef.current !== noteId) return
    const nextNote = remainingNotes[0]
    if (nextNote) openNote(nextNote)
    else resetToNewDraft()
  }

  const handleMemoChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    const content = event.target.value
    setActionMessage(null)
    memoRef.current = content
    setMemo(content)
    scheduleSave(content)
  }

  const handleNewMemo = async () => {
    if (isStartingNew || isSelectingNote || organizerSource || isArchiving || isDeleting) return
    setIsStartingNew(true)
    if (await saveContent(memoRef.current)) resetToNewDraft()
    if (isMountedRef.current) setIsStartingNew(false)
  }

  const handleSelectNote = async (note: NoteResponse) => {
    if (note.id === noteIdRef.current || isSelectingNote || isStartingNew || organizerSource || isArchiving || isDeleting) return
    setIsSelectingNote(true)
    if (await saveContent(memoRef.current)) openNote(note)
    if (isMountedRef.current) setIsSelectingNote(false)
  }

  const handleOrganize = async () => {
    if (!memoRef.current.trim() || organizerSource || isStartingNew || isSelectingNote || isArchiving || isDeleting) return
    setActionMessage(null)
    const content = memoRef.current
    const saved = await saveContent(content)
    const noteId = noteIdRef.current
    if (saved && noteId !== null) setOrganizerSource({ noteId, memo: content })
  }

  const handleArchive = async () => {
    const noteId = noteIdRef.current
    if (noteId === null || isEditorDisabled) return
    setIsArchiving(true)
    setActionMessage(null)
    try {
      if (!(await saveContent(memoRef.current))) return
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
      if (!(await saveContent(memoRef.current))) return
      await restoreNote(noteId)
      const restoredNote = await getNote(noteId)
      setNotes((currentNotes) => [restoredNote, ...currentNotes.filter((note) => note.id !== restoredNote.id)])
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

  const closeOrganizer = useCallback(() => {
    setOrganizerSource(null)
    setActionMessage(null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }, [])

  const finishOrganizer = useCallback((message: string, options?: { suggestArchiveNoteId?: number }) => {
    setOrganizerSource(null)
    setActionMessage(message)
    setArchiveSuggestionNoteId(options?.suggestArchiveNoteId ?? null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }, [])

  const isEditorDisabled =
    loadStatus !== 'ready' ||
    organizerSource !== null ||
    isStartingNew ||
    isSelectingNote ||
    isArchiving ||
    isDeleting

  return (
    <section className={`${styles['memo-card']} ${className ?? ''}`} aria-label="메모">
      {organizerSource ? (
        <TaskOrganizerPanel source={organizerSource} projects={projects} onCancel={closeOrganizer} onFinish={finishOrganizer} />
      ) : (
        <>
          <NoteEditor
            memo={memo}
            loadStatus={loadStatus}
            saveStatus={saveStatus}
            actionMessage={actionMessage}
            selectedNoteId={selectedNoteId}
            disabled={isEditorDisabled}
            isStartingNew={isStartingNew}
            isArchiving={isArchiving}
            isDeleting={isDeleting}
            isConfirmingDelete={isConfirmingDelete}
            recentlyArchived={recentlyArchivedId !== null}
            archiveSuggested={archiveSuggestionNoteId !== null}
            textareaRef={textareaRef}
            onMemoChange={handleMemoChange}
            onMemoBlur={() => void saveContent(memoRef.current)}
            onOrganize={() => void handleOrganize()}
            onNewMemo={() => void handleNewMemo()}
            onArchive={() => void handleArchive()}
            onConfirmArchive={() => {
              setArchiveSuggestionNoteId(null)
              void handleArchive()
            }}
            onDismissArchive={() => setArchiveSuggestionNoteId(null)}
            onRequestDelete={() => setIsConfirmingDelete(true)}
            onCancelDelete={() => setIsConfirmingDelete(false)}
            onDelete={() => void handleDelete()}
            onRestore={() => void handleRestoreRecent()}
          />
          <NoteList
            notes={notes}
            selectedNoteId={selectedNoteId}
            disabled={isEditorDisabled}
            projectId={projectId}
            sessionId={sessionId}
            onSelect={(note) => void handleSelectNote(note)}
          />
        </>
      )}
    </section>
  )
}
