import type { KeyboardEvent, ReactNode } from 'react'
import styles from './ModeToggle.module.css'

export interface ModeToggleOption<T extends string> {
  value: T
  label: ReactNode
  icon?: ReactNode
  disabled?: boolean
  id?: string
  controls?: string
}

interface ModeToggleProps<T extends string> {
  ariaLabel: string
  options: readonly ModeToggleOption<T>[]
  value: T
  onChange: (value: T) => void
  disabled?: boolean
  fullWidth?: boolean
  semantics?: 'group' | 'tabs'
  className?: string
}

export default function ModeToggle<T extends string>({
  ariaLabel,
  options,
  value,
  onChange,
  disabled = false,
  fullWidth = false,
  semantics = 'group',
  className,
}: ModeToggleProps<T>) {
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (semantics !== 'tabs'
      || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return

    const tabs = Array.from(
      event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]:not(:disabled)'),
    )
    if (tabs.length === 0) return

    event.preventDefault()
    const currentIndex = tabs.indexOf(document.activeElement as HTMLButtonElement)
    const nextIndex = event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? tabs.length - 1
        : event.key === 'ArrowLeft'
          ? (currentIndex - 1 + tabs.length) % tabs.length
          : (currentIndex + 1) % tabs.length
    tabs[nextIndex]?.focus()
    tabs[nextIndex]?.click()
  }

  return (
    <div
      className={`${styles.root} ${fullWidth ? styles['full-width'] : ''} ${className ?? ''}`}
      role={semantics === 'tabs' ? 'tablist' : 'group'}
      aria-label={ariaLabel}
      onKeyDown={handleKeyDown}
    >
      {options.map((option) => {
        const isActive = option.value === value
        return (
          <button
            key={option.value}
            id={option.id}
            className={styles.button}
            type="button"
            role={semantics === 'tabs' ? 'tab' : undefined}
            aria-selected={semantics === 'tabs' ? isActive : undefined}
            aria-pressed={semantics === 'group' ? isActive : undefined}
            aria-controls={semantics === 'tabs' ? option.controls : undefined}
            tabIndex={semantics === 'tabs' ? (isActive ? 0 : -1) : undefined}
            disabled={disabled || option.disabled}
            onClick={() => onChange(option.value)}
          >
            {option.icon && <span className={styles.icon} aria-hidden="true">{option.icon}</span>}
            <span>{option.label}</span>
          </button>
        )
      })}
    </div>
  )
}
