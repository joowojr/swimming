import styles from './TaskChecklist.module.css'

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
  highlightSelected?: boolean
  onToggle: (id: number) => void
}

export default function TaskChecklist({
  items,
  selectedIds,
  name,
  emptyMessage,
  disabled = false,
  highlightSelected = true,
  onToggle,
}: TaskChecklistProps) {
  if (items.length === 0) {
    return <p className={styles.empty}>{emptyMessage}</p>
  }

  return (
    <ul className={styles.list}>
      {items.map((item) => (
        <li key={item.id}>
          <label
            className={`${styles.option} ${highlightSelected ? styles['highlight-selected'] : ''}`}
          >
            <input
              type="checkbox"
              name={name}
              value={item.id}
              checked={selectedIds.includes(item.id)}
              onChange={() => onToggle(item.id)}
              disabled={disabled}
            />
            <span className={styles.text}>
              <span className={styles.title}>{item.title}</span>
              {item.description && (
                <span className={styles.description}>{item.description}</span>
              )}
            </span>
          </label>
        </li>
      ))}
    </ul>
  )
}
