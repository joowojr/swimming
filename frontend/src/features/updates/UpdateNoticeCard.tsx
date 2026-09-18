import { useState } from 'react'
import { IconX } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import { UPDATE_NOTES, currentUpdateNote } from './updateNotes'
import styles from './UpdateNoticeCard.module.css'

/** 마지막으로 닫은 소식의 id. 새 배포의 소식은 id가 달라 다시 뜬다. */
const DISMISSED_KEY = 'swimming-dismissed-update-9-18'

function readDismissedId() {
  try {
    return localStorage.getItem(DISMISSED_KEY)
  } catch {
    return null
  }
}

/**
 * 역할: 이번 배포에서 새로 생긴 기능을 왼쪽 아래 플로팅 독 옆에 한 번 알린다.
 *
 * 무엇을 알릴지는 updateNotes가 정한다. 닫으면 이 소식은 다시 뜨지 않고, 닫지 않아도
 * 표시 기간이 지나면 사라진다. 저장소를 못 쓰는 브라우저에서는 닫기가 이번 방문에만 남는다.
 *
 * 독 위젯이 펼쳐지면 이 알림을 덮는다. 알림이 비켜 서면 독을 쓰는 동안 화면이 흔들린다.
 */
export default function UpdateNoticeCard() {
  // 화면을 연 동안 날짜가 바뀌어도 알림이 갑자기 사라지지 않게, 처음 그릴 때 한 번만 고른다.
  const [note] = useState(() => currentUpdateNote(UPDATE_NOTES, Date.now()))
  const [dismissedId, setDismissedId] = useState(readDismissedId)

  if (!note || dismissedId === note.id) return null

  const dismiss = () => {
    try {
      localStorage.setItem(DISMISSED_KEY, note.id)
    } catch {
      // 저장하지 못해도 이번 방문에서는 닫는다.
    }
    setDismissedId(note.id)
  }

  return (
    <aside className={styles.notice} aria-label="새로 생긴 기능 안내">
      <div className={styles.header}>
        <div className={styles.heading}>
          <strong>업데이트 노트</strong>
          <time className={styles.date} dateTime={note.publishedAt}>
            {note.publishedAt.replaceAll('-', '.')}
          </time>
        </div>
        <button type="button" className={styles.close} aria-label="새 기능 안내 닫기" onClick={dismiss}>
          <IconX size={15} stroke={1.8} aria-hidden="true" />
        </button>
      </div>

      <ul className={styles.items}>
        {note.items.map(({ icon: Icon, title, description }) => (
          <li key={title} className={styles.item}>
            <span className={styles.icon} aria-hidden="true">
              <Icon size={16} stroke={1.8} />
            </span>
            <div className={styles.itemContent}>
              <strong>{title}</strong>
              <p>{description}</p>
            </div>
          </li>
        ))}
      </ul>

      <div className={styles.footer}>
        <ActionButton className={styles.confirm} variant="plain" onClick={dismiss}>확인했어요</ActionButton>
      </div>
    </aside>
  )
}
