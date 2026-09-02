import type { ReactNode } from 'react'
import { IconCheck, IconPlayerPause } from '@tabler/icons-react'
import type { TaskStatus } from '../features/tasks/taskTypes'
import styles from './ChecklistCard.module.css'

interface ChecklistCardBaseProps {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  variant?: 'default' | 'flat'
  ariaLabel: string
  disabled?: boolean
  onToggle: () => void
}

interface ChecklistCardSelectionProps extends ChecklistCardBaseProps {
  id: number
  checked: boolean
  name?: string
  status?: never
}

interface ChecklistCardStatusProps extends ChecklistCardBaseProps {
  // Task의 진행 상태를 그대로 보여주는 표시등. 눌러서 완료/미완료를 오간다.
  status: TaskStatus
  id?: never
  checked?: never
  name?: never
}

type ChecklistCardProps = ChecklistCardSelectionProps | ChecklistCardStatusProps

function hasStatus(props: ChecklistCardProps): props is ChecklistCardStatusProps {
  return props.status !== undefined
}

export default function ChecklistCard(props: ChecklistCardProps) {
  const { title, description, actions, variant = 'default', ariaLabel, disabled = false, onToggle } = props

  return (
    <div className={`${styles.card} ${variant === 'flat' ? styles.flat : ''}`}>
      <div className={styles.identity}>
        {hasStatus(props) ? (
          <button
            className={styles.status}
            type="button"
            data-status={props.status}
            aria-label={ariaLabel}
            aria-pressed={props.status === 'DONE'}
            disabled={disabled}
            onClick={onToggle}
          >
            {props.status === 'DONE' ? <IconCheck size={16} stroke={2.2} aria-hidden="true" /> : null}
            {props.status === 'HOLD' ? <IconPlayerPause size={14} stroke={2} aria-hidden="true" /> : null}
            {props.status === 'DOING' ? <span className={styles['status-core']} aria-hidden="true" /> : null}
          </button>
        ) : (
          <input
            className={styles.checkbox}
            type="checkbox"
            id={props.name ? `${props.name}-${props.id}` : undefined}
            name={props.name}
            value={props.id}
            checked={props.checked}
            aria-label={ariaLabel}
            onChange={onToggle}
            disabled={disabled}
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
