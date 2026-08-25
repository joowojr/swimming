import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import styles from './Button.module.css'

interface ActionButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon?: ReactNode
  isLoading?: boolean
  loadingLabel?: string
  variant?: 'primary' | 'outline' | 'plain'
}

export default function ActionButton({
  children,
  className,
  disabled,
  icon,
  isLoading = false,
  loadingLabel,
  type = 'button',
  variant = 'primary',
  ...buttonProps
}: ActionButtonProps) {
  return (
    <button
      {...buttonProps}
      type={type}
      className={`${styles.button} ${styles[variant]} ${className ?? ''}`}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
    >
      {isLoading ? <IconLoader2 className={styles.spinner} aria-hidden="true" /> : icon}
      {isLoading ? loadingLabel ?? children : children}
    </button>
  )
}
