import type { NoteResponse } from './noteTypes'
import type { NoteContextType } from './noteTypes'
import styles from './NoteCard.module.css'

export type NoteListFilter = NoteContextType | 'ALL' | 'ARCHIVED'

/** 역할: 저장된 메모 목록을 표시하고, 선택 이벤트만 상위 컴포넌트에 전달한다. */
interface NoteListProps {
  notes: NoteResponse[]
  selectedNoteId: number | null
  disabled: boolean
  folderId?: number
  sessionId?: number
  filter: NoteListFilter
  onFilterChange: (filter: NoteListFilter) => void
  onSelect: (note: NoteResponse) => void
}

const noteDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
})

function getNotePreview(content: string) {
  return content.split(/\r?\n/, 1)[0].trim() || '메모'
}

function formatNoteDate(createdAt: string) {
  const createdDate = new Date(createdAt)
  return Number.isNaN(createdDate.getTime()) ? '' : noteDateFormatter.format(createdDate)
}

export default function NoteList({
  notes,
  selectedNoteId,
  disabled,
  folderId,
  sessionId,
  filter,
  onFilterChange,
  onSelect,
}: NoteListProps) {
  const isPinboardScreen = folderId === undefined && sessionId === undefined

  return (
    <section className={styles['memo-list']} aria-labelledby="saved-memos-title">
      <div className={styles['memo-list-head']}>
        <label className={styles['memo-list-filter']}>
          <span className="sr-only">메모 목록 컨텍스트</span>
          <select id="saved-memos-title" value={filter} onChange={(event) => onFilterChange(event.target.value as NoteListFilter)}>
            <option value="DEFAULT">핀보드</option>
            <option value="SESSION">세션</option>
            <option value="FOLDER">폴더</option>
            <option value="ALL">전체</option>
            <option value="ARCHIVED">보관함</option>
          </select>
        </label>
        <span>{notes.length}개</span>
      </div>

      {notes.length > 0 ? (
        <ul className={styles['memo-list-items']}>
          {notes.map((note) => (
            <li key={note.id}>
              {/** 현재 페이지 컨텍스트에 속한 메모인지 목록에서도 바로 구분한다. */}
              <button
                type="button"
                className={selectedNoteId === note.id
                  ? styles['memo-list-action-active']
                  : styles['memo-list-action']}
                onClick={() => onSelect(note)}
                disabled={disabled}
                aria-pressed={selectedNoteId === note.id}
              >
                {filter === 'ALL' && (
                  (folderId !== undefined && note.folderId === folderId)
                  || (sessionId !== undefined && note.sessionId === sessionId)
                  || (isPinboardScreen && note.contextType === 'DEFAULT')
                ) && (
                  <span className={styles['memo-list-context-dot']} role="img" aria-label="현재 페이지의 메모" />
                )}
                <span>{getNotePreview(note.content)}</span>
                <time dateTime={note.createdAt}>{formatNoteDate(note.createdAt)}</time>
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className={styles['memo-list-empty']}>저장된 메모가 여기에 모여요.</p>
      )}
    </section>
  )
}
