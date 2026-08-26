import styles from './TaskChecklist.module.css'
import ChecklistCard from './ChecklistCard'

interface TaskChecklistItem {
  id: number
  title: string
  description?: string
}

interface TaskChecklistProps {
  items: readonly TaskChecklistItem[]
  selectedIds: readonly number[]
  name: string
  emptyMessage: string
  disabled?: boolean
  onToggle: (id: number) => void
}

export default function TaskChecklist({
  items,
  selectedIds,
  name,
  emptyMessage,
  disabled = false,
  onToggle,
}: TaskChecklistProps) {
  if (items.length === 0) {
    return <p className={styles.empty}>{emptyMessage}</p>
  }

  return (
    <ul className={styles.list}>
      {items.map((item) => (
        <li key={item.id}>
          <ChecklistCard
            id={item.id}
            title={item.title}
            description={item.description}
            checked={selectedIds.includes(item.id)}
            ariaLabel={`${item.title} 선택`}
            name={name}
            disabled={disabled}
            onToggle={() => onToggle(item.id)}
          />
        </li>
      ))}
    </ul>
  )
}
