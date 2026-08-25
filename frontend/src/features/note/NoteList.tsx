import type { NoteResponse } from './noteTypes'
import styles from './NoteCard.module.css'

/** 역할: 저장된 메모 목록을 표시하고, 선택 이벤트만 상위 컴포넌트에 전달한다. */
interface NoteListProps {
  notes: NoteResponse[]
  selectedNoteId: number | null
  disabled: boolean
  projectId?: number
  sessionId?: number
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
  projectId,
  sessionId,
  onSelect,
}: NoteListProps) {
  return (
    <section className={styles['memo-list']} aria-labelledby="saved-memos-title">
      <div className={styles['memo-list-head']}>
        <h4 id="saved-memos-title">메모 목록</h4>
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
                {(note.projectId === projectId || note.sessionId === sessionId) && (
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
