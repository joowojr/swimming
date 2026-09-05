import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { IconTrash } from '@tabler/icons-react'
import styles from './DeleteIconButton.module.css'

/** 역할: 삭제 동작을 시작하거나 확정하는 버튼의 아이콘·접근성·상태 표현을 통일한다. */
interface DeleteIconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  label: string
  active?: boolean
  iconSize?: number
  /** 취소처럼 삭제가 아닌 동작을 이 버튼으로 표현할 때 쓰레기통을 숨긴다. */
  hideIcon?: boolean
  children?: ReactNode
}

export default function DeleteIconButton({
  label,
  active = false,
  iconSize = 16,
  hideIcon = false,
  children,
  className,
  ...buttonProps
}: DeleteIconButtonProps) {
  return (
    <button
      {...buttonProps}
      type="button"
      className={`${styles.button} ${active ? styles.active : ''} ${className ?? ''}`}
      aria-label={label}
      aria-pressed={active || undefined}
    >
      {!hideIcon && <IconTrash size={iconSize} aria-hidden="true" />}
      {children}
    </button>
  )
}
