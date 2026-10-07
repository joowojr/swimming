import { IconCircleCheck, IconHelp, IconRefresh } from '@tabler/icons-react'
import type { LaneDefinition } from './agentWorkLabels'
import type { BoardLane } from './agentWorkTypes'
import type { BoardEntry } from './agentWorkBoard'
import { workItemKey } from './agentWorkKeys'
import SessionCard from './SessionCard'
import WorkItemCard from './WorkItemCard'
import styles from './BoardLaneSection.module.css'

interface BoardLaneSectionProps {
  definition: LaneDefinition
  entries: BoardEntry[]
  selectedKey: string | null
  onSelect: (key: string) => void
}

function LaneMarker({ lane }: { lane: BoardLane }) {
  switch (lane) {
    case 'WORKING':
      return <IconRefresh className={styles['icon-working']} size={18} stroke={2} aria-hidden="true" />
    case 'COMPLETED':
      return <IconCircleCheck className={styles['icon-muted']} size={18} stroke={2} aria-hidden="true" />
    case 'ATTENTION':
      return <IconHelp className={styles['icon-muted']} size={18} stroke={2} aria-hidden="true" />
    case 'WAITING':
      return <span className={`${styles.dot} ${styles['dot-waiting']}`} aria-hidden="true" />
    case 'NOT_STARTED':
      return <span className={styles.dot} aria-hidden="true" />
  }
}

export default function BoardLaneSection({ definition, entries, selectedKey, onSelect }: BoardLaneSectionProps) {
  const headingId = `lane-${definition.lane.toLowerCase()}`

  return (
    <section className={styles.lane} data-lane={definition.lane} aria-labelledby={headingId}>
      <header className={styles.header}>
        <LaneMarker lane={definition.lane} />
        <h3 id={headingId}>{definition.title}</h3>
        <span className={styles.count}>{entries.length}</span>
      </header>
      {entries.length === 0 ? (
        <p className={styles.empty}>비어 있어요</p>
      ) : (
        <ul className={styles.grid}>
          {entries.map((entry) => (
            <li key={entry.key}>
              {entry.kind === 'session' ? (
                <SessionCard
                  session={entry.session}
                  cards={entry.cards}
                  entryKey={entry.key}
                  isSelected={entry.key === selectedKey
                    || entry.cards.some((card) => workItemKey(card) === selectedKey)}
                  onSelect={onSelect}
                />
              ) : (
                <WorkItemCard
                  item={entry.card}
                  isSelected={entry.key === selectedKey}
                  onSelect={onSelect}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
