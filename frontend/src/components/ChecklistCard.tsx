import type { ReactNode } from 'react'
import styles from './ChecklistCard.module.css'

interface ChecklistCardBaseProps {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  variant?: 'default' | 'flat'
}

interface ChecklistCardSelectionProps extends ChecklistCardBaseProps {
  id: number
  checked: boolean
  ariaLabel: string
  name?: string
  disabled?: boolean
  onToggle: () => void
  leadingControl?: never
}

interface ChecklistCardDisplayProps extends ChecklistCardBaseProps {
  leadingControl: ReactNode
  id?: never
  checked?: never
  ariaLabel?: never
  name?: never
  disabled?: never
  onToggle?: never
}

type ChecklistCardProps = ChecklistCardSelectionProps | ChecklistCardDisplayProps

function hasLeadingControl(props: ChecklistCardProps): props is ChecklistCardDisplayProps {
  return props.leadingControl !== undefined
}

export default function ChecklistCard(props: ChecklistCardProps) {
  const { title, description, actions, variant = 'default' } = props

  return (
    <div className={`${styles.card} ${variant === 'flat' ? styles.flat : ''}`}>
      <div className={styles.identity}>
        {hasLeadingControl(props) ? props.leadingControl : (
          <input
            className={styles.checkbox}
            type="checkbox"
            id={props.name ? `${props.name}-${props.id}` : undefined}
            name={props.name}
            value={props.id}
            checked={props.checked}
            aria-label={props.ariaLabel}
            onChange={props.onToggle}
            disabled={props.disabled ?? false}
          />
        )}
        <div className={styles.copy}>
          {description && <span className={styles.description}>{description}</span>}
          <span className={styles.title}>{title}</span>
        </div>
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  )
}
