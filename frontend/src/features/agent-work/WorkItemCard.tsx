import AgentIcon from './AgentIcon'
import { STATUS_LABELS, UNCATEGORIZED_LABEL, formatRelativeTime } from './agentWorkLabels'
import type { AgentSession, AgentWorkItem } from './agentWorkTypes'
import { workItemKey } from './agentWorkKeys'
import styles from './WorkItemCard.module.css'

interface WorkItemCardProps {
  item: AgentWorkItem
  isSelected: boolean
  onSelect: (key: string) => void
}

const ENDED_MARKS: Partial<Record<AgentSession['status'], string>> = {
  COMPLETED: '✓',
  FAILED: '!',
  UNKNOWN: '?',
}

/** 판단을 기다리는 질문은 따옴표로 감싸 에이전트의 말임을 드러낸다. */
function formatSummary(session: AgentSession | null) {
  if (!session?.summary) return null
  return session.status === 'WAITING' ? `"${session.summary}"` : session.summary
}

function SessionStatus({ session }: { session: AgentSession }) {
  const mark = ENDED_MARKS[session.status]
  if (mark) {
    return <span>{`${mark} ${STATUS_LABELS[session.status]}`}</span>
  }
  return (
    <span className={styles.status}>
      <span className={styles.dot} data-status={session.status} aria-hidden="true" />
      {STATUS_LABELS[session.status]}
    </span>
  )
}

export default function WorkItemCard({ item, isSelected, onSelect }: WorkItemCardProps) {
  const { workItem, session, lastActivityAt } = item
  const summary = formatSummary(session)

  return (
    <button
      type="button"
      className={styles.card}
      data-lane={item.lane}
      // Lane이 바뀌어도 같은 카드로 이어 보이도록 카드마다 고유한 전환 이름을 준다.
      style={{ viewTransitionName: `agent-card-${workItem.type}-${workItem.id}` }}
      aria-pressed={isSelected}
      onClick={() => onSelect(workItemKey(item))}
    >
      <span className={styles.top}>
        {(workItem.urgent || workItem.important) && (
          <span className={styles.flags}>
            {workItem.urgent && <span className={styles.urgent}>⚡️ 즉시</span>}
            {workItem.important && <span className={styles.important}>📌 중요</span>}
          </span>
        )}
        <span className={styles.title}>{workItem.title}</span>
      </span>

      {session && (
        <span className={styles.session}>
          <AgentIcon className={styles.agent} agentType={session.agentType} />
          <SessionStatus session={session} />
        </span>
      )}

      {summary && <span className={styles.summary}>{summary}</span>}

      <span className={styles.footer}>
        <span>{workItem.containerName ?? UNCATEGORIZED_LABEL} · {formatRelativeTime(lastActivityAt)}</span>
      </span>
    </button>
  )
}
