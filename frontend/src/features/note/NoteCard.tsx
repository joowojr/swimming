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
import type { NoteListFilter } from './NoteList'
import { useNoteEditorStore } from './noteEditorStore'
import TaskOrganizerPanel from './TaskOrganizerPanel'
import type { NoteContextType, NoteResponse } from './noteTypes'
import type { LoadStatus, ProjectOption, SaveStatus } from './noteViewTypes'
import styles from './NoteCard.module.css'

/**
 * 역할: 노트의 선택·자동 저장·보관·삭제를 관리하고, 각 화면 조각과 Task Organizer 흐름을 조합한다.
 * AI 정리와 캘린더 연결의 세부 상태는 TaskOrganizerPanel이 소유한다.
 */
interface NoteCardProps {
  folders: ProjectOption[]
  folderId?: number
  sessionId?: number
  className?: string
  // 방금 만들어진 세션처럼 노트가 없는 것이 확실할 때, 첫 조회를 건너뛴다.
  skipInitialLoad?: boolean
  onInitialLoadSkip?: () => void
}

interface OrganizerSource {
  noteId: number
  memo: string
  contextType: NoteContextType
  contextId?: number
}

const AUTO_SAVE_DELAY_MS = 700

export default function NoteCard({
                                   folders,
                                   folderId,
                                   sessionId,
                                   className,
                                   skipInitialLoad = false,
                                   onInitialLoadSkip,
                                 }: NoteCardProps) {
  const [memo, setMemo] = useState('')
  const [notes, setNotes] = useState<NoteResponse[]>([])
  const [noteFilter, setNoteFilter] = useState<NoteListFilter>(
      sessionId !== undefined ? 'SESSION' : folderId !== undefined ? 'FOLDER' : 'DEFAULT',
  )
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null)
  // 첫 조회를 건너뛰는 경우에는 기다릴 것이 없으므로 처음부터 준비된 상태다.
  const [loadStatus, setLoadStatus] = useState<LoadStatus>(skipInitialLoad ? 'ready' : 'loading')
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle')
  const [organizerSource, setOrganizerSource] = useState<OrganizerSource | null>(null)
  const [isStartingNew, setIsStartingNew] = useState(false)
  const [isSelectingNote, setIsSelectingNote] = useState(false)
  const [isArchiving, setIsArchiving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const {
    recentlyArchivedId,
    setIsConfirmingDelete,
    setRecentlyArchivedId,
    setArchiveSuggestionNoteId,
    setActionMessage,
    reset: resetEditorStore,
  } = useNoteEditorStore()

  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const memoRef = useRef('')
  const noteIdRef = useRef<number | null>(null)
  const lastSavedContentRef = useRef('')
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const saveQueueRef = useRef<Promise<void>>(Promise.resolve())
  const pendingContentRef = useRef<string | null>(null)
  const pendingSaveRef = useRef<Promise<boolean> | null>(null)
  const isMountedRef = useRef(true)
  // 이미 불러온 필터. 첫 조회를 건너뛴 경우에도 필터 변경 이펙트가 대신 조회하지 않게 한다.
  const loadedFilterRef = useRef<NoteListFilter | null>(null)
  // 스킵 여부는 마운트 시점 값으로 고정한다. 부모가 플래그를 내렸다고 다시 조회하면 안 되고,
  // StrictMode가 이펙트를 두 번 실행해도 두 번 다 스킵해야 한다.
  const skipInitialLoadRef = useRef(skipInitialLoad)
  const onInitialLoadSkipRef = useRef(onInitialLoadSkip)

  useEffect(() => {
    onInitialLoadSkipRef.current = onInitialLoadSkip
  }, [onInitialLoadSkip])

  const getNotesForFilter = useCallback((filter: NoteListFilter) => {
    if (filter === 'ALL') return getNotes()
    if (filter === 'ARCHIVED') return getNotes({ status: 'ARCHIVED' })
    if (filter === 'SESSION' && sessionId !== undefined) return getNotes({ sessionId })
    if (filter === 'FOLDER' && folderId !== undefined) return getNotes({ folderId })
    return getNotes({ contextType: filter })
  }, [folderId, sessionId])

  useEffect(() => {
    isMountedRef.current = true
    loadedFilterRef.current = null

    const defaultFilter: NoteListFilter = sessionId !== undefined
        ? 'SESSION'
        : folderId !== undefined ? 'FOLDER' : 'DEFAULT'
    let cancelled = false

    // 폴더·세션이 바뀌면 부모가 key로 새 인스턴스를 만들므로, 여기서 이전 상태를 비울 필요가 없다.
    resetEditorStore()

    if (skipInitialLoadRef.current) {
      loadedFilterRef.current = defaultFilter
      onInitialLoadSkipRef.current?.()

      return () => {
        cancelled = true
        isMountedRef.current = false
        if (saveTimerRef.current) clearTimeout(saveTimerRef.current)
      }
    }

    async function loadLatestNote() {
      try {
        const loadedNotes = await getNotesForFilter(defaultFilter)
        if (cancelled) return

        const latestNote = loadedNotes[0]
        const content = latestNote?.content ?? ''

        noteIdRef.current = latestNote?.id ?? null
        memoRef.current = content
        lastSavedContentRef.current = content
        loadedFilterRef.current = defaultFilter

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
  }, [getNotesForFilter, folderId, resetEditorStore, sessionId])

  useEffect(() => {
    // 최초 조회가 진행 중이거나, 이미 현재 필터의 목록을 갖고 있으면 재조회하지 않는다.
    if (loadedFilterRef.current === null || loadedFilterRef.current === noteFilter) return

    let cancelled = false

    void getNotesForFilter(noteFilter)
        .then((loadedNotes) => {
          if (cancelled) return
          loadedFilterRef.current = noteFilter
          setNotes(loadedNotes)
        })
        .catch(() => {
          if (!cancelled) setActionMessage('노트 목록을 불러오지 못했어요')
        })

    return () => {
      cancelled = true
    }
  }, [getNotesForFilter, noteFilter, setActionMessage])

  useEffect(() => {
    if (recentlyArchivedId === null) return
    const timer = window.setTimeout(() => setRecentlyArchivedId(null), 3000)
    return () => window.clearTimeout(timer)
  }, [recentlyArchivedId, setRecentlyArchivedId])

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
                ? { content, contextType: 'SESSION', folderId: null, sessionId }
                : folderId === undefined
                    ? { content, contextType: 'DEFAULT', folderId: null, sessionId: null }
                    : { content, contextType: 'FOLDER', folderId, sessionId: null })
            : await updateNote(noteIdRef.current, { content })

        noteIdRef.current = savedNote.id
        lastSavedContentRef.current = savedNote.content
        if (isMountedRef.current) {
          setNotes((currentNotes) => [
            savedNote,
            ...currentNotes.filter((note) => note.id !== savedNote.id),
          ])
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
    if (saved && noteId !== null) {
      // 노트가 속한 범위를 그대로 넘겨 그 폴더(또는 세션의 폴더들)만 참조하게 한다.
      setOrganizerSource({
        noteId,
        memo: content,
        contextType: sessionId !== undefined
            ? 'SESSION'
            : folderId !== undefined ? 'FOLDER' : 'DEFAULT',
        contextId: sessionId ?? folderId,
      })
    }
  }

  const handleArchive = async () => {
    const noteId = noteIdRef.current
    if (noteId === null || isEditorDisabled) return
    setIsArchiving(true)
    setArchiveSuggestionNoteId(null)
    setActionMessage(null)
    try {
      if (!(await saveContent(memoRef.current))) return
      await archiveNote(noteId)
      removeNoteFromList(noteId)
      setRecentlyArchivedId(noteId)
    } catch {
      setActionMessage('노트를 보관하지 못했어요')
    } finally {
      if (isMountedRef.current) setIsArchiving(false)
    }
  }

  const handleRestoreRecent = async () => {
    const noteId = recentlyArchivedId
    if (noteId === null || isArchiving) return
    setIsArchiving(true)
    setArchiveSuggestionNoteId(null)
    setActionMessage(null)
    try {
      if (!(await saveContent(memoRef.current))) return
      await restoreNote(noteId)
      const restoredNote = await getNote(noteId)
      setNotes((currentNotes) => [restoredNote, ...currentNotes.filter((note) => note.id !== restoredNote.id)])
      setRecentlyArchivedId(null)
      openNote(restoredNote)
    } catch {
      setActionMessage('노트를 복원하지 못했어요')
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
      if (!(await saveContent(memoRef.current))) return
      await deleteNote(noteId)
      removeNoteFromList(noteId)
      setRecentlyArchivedId(null)
      setArchiveSuggestionNoteId(null)
      setIsConfirmingDelete(false)
    } catch {
      setActionMessage('노트를 삭제하지 못했어요')
    } finally {
      if (isMountedRef.current) setIsDeleting(false)
    }
  }

  const handleRequestDelete = () => {
    setRecentlyArchivedId(null)
    setArchiveSuggestionNoteId(null)
    setIsConfirmingDelete(true)
  }

  const closeOrganizer = useCallback(() => {
    setOrganizerSource(null)
    setActionMessage(null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }, [setActionMessage])

  const finishOrganizer = useCallback((message: string, options?: { suggestArchiveNoteId?: number }) => {
    setOrganizerSource(null)
    setActionMessage(message)
    setArchiveSuggestionNoteId(options?.suggestArchiveNoteId ?? null)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }, [setActionMessage, setArchiveSuggestionNoteId])

  const isEditorDisabled =
      loadStatus !== 'ready' ||
      organizerSource !== null ||
      isStartingNew ||
      isSelectingNote ||
      isArchiving ||
      isDeleting
  const isSelectedNoteArchived = noteFilter === 'ARCHIVED'
      || notes.find((note) => note.id === selectedNoteId)?.status === 'ARCHIVED'

  return (
      <section className={`${styles['memo-card']} ${className ?? ''}`} aria-label="노트">
        {organizerSource ? (
            <TaskOrganizerPanel source={organizerSource} folders={folders} onCancel={closeOrganizer} onFinish={finishOrganizer} />
        ) : (
            <>
              <NoteEditor
                  memo={memo}
                  loadStatus={loadStatus}
                  saveStatus={saveStatus}
                  selectedNoteId={selectedNoteId}
                  isArchived={isSelectedNoteArchived}
                  disabled={isEditorDisabled}
                  isStartingNew={isStartingNew}
                  isArchiving={isArchiving}
                  isDeleting={isDeleting}
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
                  onRequestDelete={handleRequestDelete}
                  onCancelDelete={() => setIsConfirmingDelete(false)}
                  onDelete={() => void handleDelete()}
                  onRestore={() => void handleRestoreRecent()}
              />
              <NoteList
                  notes={notes}
                  selectedNoteId={selectedNoteId}
                  disabled={isEditorDisabled}
                  folderId={folderId}
                  sessionId={sessionId}
                  filter={noteFilter}
                  onFilterChange={setNoteFilter}
                  onSelect={(note) => void handleSelectNote(note)}
              />
            </>
        )}
      </section>
  )
}