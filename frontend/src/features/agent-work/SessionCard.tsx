import AgentLabel from './AgentLabel'
import { sessionTitle } from './agentWorkBoard'
import { STATUS_LABELS, formatRelativeTime } from './agentWorkLabels'
import type { AgentSession, AgentWorkItem } from './agentWorkTypes'
import styles from './SessionCard.module.css'

interface SessionCardProps {
  session: AgentSession
  /** 이 세션에 붙어 있는 할 일 가운데 지금 보드에 보이는 것. */
  cards: AgentWorkItem[]
  entryKey: string
  isSelected: boolean
  onSelect: (key: string) => void
}

const ENDED_MARKS: Partial<Record<AgentSession['status'], string>> = {
  COMPLETED: '✓',
  FAILED: '!',
  UNKNOWN: '?',
}

export default function SessionCard({ session, cards, entryKey, isSelected, onSelect }: SessionCardProps) {
  const mark = ENDED_MARKS[session.status]
  // 세션에 붙은 할 일 가운데 필터 때문에 보이지 않는 것은 숫자로만 알린다.
  const hiddenCount = session.workItemIds.length - cards.length

  return (
    <button
      type="button"
      className={styles.card}
      data-status={session.status}
      style={{ viewTransitionName: `agent-session-${session.id}` }}
      aria-pressed={isSelected}
      onClick={() => onSelect(entryKey)}
    >
      <span className={styles.top}>
        <AgentLabel className={styles.agent} agentType={session.agentType} />
        {mark ? (
          <span className={styles.status}>{`${mark} ${STATUS_LABELS[session.status]}`}</span>
        ) : (
          <span className={styles.status}>
            <span className={styles.dot} data-status={session.status} aria-hidden="true" />
            {STATUS_LABELS[session.status]}
          </span>
        )}
      </span>

      <strong className={styles.title}>{sessionTitle(session)}</strong>

      {session.summary && (
        <span className={styles.summary}>
          {session.status === 'WAITING' ? `"${session.summary}"` : session.summary}
        </span>
      )}

      <span className={styles['work-items']}>
        {cards.map((card) => (
          <span className={styles['work-item']} key={card.workItem.id}>
            {(card.workItem.urgent || card.workItem.important) && (
              <span aria-hidden="true">{card.workItem.urgent ? '⚡️' : ''}{card.workItem.important ? '📌' : ''} </span>
            )}
            {card.workItem.title}
          </span>
        ))}
        {hiddenCount > 0 && <span className={styles.hidden}>필터에 걸린 할 일 {hiddenCount}개</span>}
      </span>

      <span className={styles.footer}>
        <span>할 일 {session.workItemIds.length}개 · {formatRelativeTime(session.lastSeenAt)}</span>
      </span>
    </button>
  )
}
