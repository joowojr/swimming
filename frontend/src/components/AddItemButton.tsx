import { IconLoader2, IconPlus } from '@tabler/icons-react'
import type { ButtonHTMLAttributes } from 'react'
import styles from './AddItemButton.module.css'

interface AddItemButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  isLoading?: boolean
}

export default function AddItemButton({
  className,
  isLoading = false,
  disabled,
  type = 'button',
  ...props
}: AddItemButtonProps) {
  return (
    <button
      {...props}
      type={type}
      className={`${styles.button} ${className ?? ''}`}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
    >
      {isLoading
        ? <IconLoader2 className={styles.spinner} size={14} stroke={1.5} aria-hidden="true" />
        : <IconPlus size={14} stroke={1.5} aria-hidden="true" />}
    </button>
  )
}
