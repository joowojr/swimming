import type { ButtonHTMLAttributes } from 'react'
import styles from './LoadMoreButton.module.css'

interface LoadMoreButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  isLoading?: boolean
}

/** 목록 끝에서 다음 페이지를 받는 버튼. 목록은 무한 스크롤이 아니라 이 버튼으로 이어 받는다. */
export default function LoadMoreButton({
  className,
  isLoading = false,
  disabled,
  type = 'button',
  ...props
}: LoadMoreButtonProps) {
  return (
    <button
      {...props}
      type={type}
      className={`${styles.button} ${className ?? ''}`}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
    >
      {isLoading ? '불러오는 중' : '더 보기'}
    </button>
  )
}
