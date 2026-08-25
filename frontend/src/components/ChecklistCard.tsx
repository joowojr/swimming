import type { ReactNode } from 'react'
import styles from './ChecklistCard.module.css'

interface ChecklistCardProps {
  id: number
  title: ReactNode
  description?: ReactNode
  checked: boolean
  ariaLabel: string
  name?: string
  disabled?: boolean
  actions?: ReactNode
  onToggle: () => void
}

export default function ChecklistCard({
  id,
  title,
  description,
  checked,
  ariaLabel,
  name,
  disabled = false,
  actions,
  onToggle,
}: ChecklistCardProps) {
  return (
    <div className={styles.card}>
      <div className={styles.identity}>
        <input
          className={styles.checkbox}
          type="checkbox"
          id={name ? `${name}-${id}` : undefined}
          name={name}
          value={id}
          checked={checked}
          aria-label={ariaLabel}
          onChange={onToggle}
          disabled={disabled}
        />
        <div className={styles.copy}>
          {description && <span className={styles.description}>{description}</span>}
          <span className={styles.title}>{title}</span>
        </div>
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  )
}
