import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import styles from './Button.module.css'

interface ModalTriggerButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  dialogId?: string
  icon?: ReactNode
  isOpen?: boolean
  isPreparing?: boolean
  preparingLabel?: string
  variant?: 'primary' | 'outline' | 'plain'
}

export default function ModalTriggerButton({
  children,
  className,
  dialogId,
  disabled,
  icon,
  isOpen,
  isPreparing = false,
  preparingLabel,
  type = 'button',
  variant = 'primary',
  ...buttonProps
}: ModalTriggerButtonProps) {
  return (
    <button
      {...buttonProps}
      type={type}
      className={`${styles.button} ${styles[variant]} ${className ?? ''}`}
      disabled={disabled || isPreparing}
      aria-busy={isPreparing || undefined}
      aria-controls={dialogId}
      aria-expanded={isOpen}
      aria-haspopup="dialog"
    >
      {isPreparing
        ? <IconLoader2 className={styles.spinner} aria-hidden="true" />
        : icon}
      {isPreparing ? preparingLabel ?? children : children}
    </button>
  )
}
