import { useEffect, useRef, useState, type ReactNode } from 'react'
import { IconFilter } from '@tabler/icons-react'
import styles from './FilterMenu.module.css'

/**
 * 역할: 목록을 좁히는 조건을 담는 필터 메뉴의 껍데기다. 트리거 버튼과 바깥 클릭·Escape로
 * 닫는 규칙만 맡고, 어떤 조건을 보여줄지는 호출한 화면이 children으로 정한다.
 *
 * 패널 안의 항목은 화면마다 같은 모양이어야 하므로 호출부가 `FilterMenu.module.css`의
 * `field`·`check`·`reset`을 그대로 가져다 쓴다.
 */
interface FilterMenuProps {
  /** 패널의 이름. 어떤 목록을 좁히는 필터인지 읽어 준다. */
  ariaLabel: string
  children: ReactNode
  /** 적용한 조건 수. 0이면 트리거에 표시하지 않는다. */
  activeCount?: number
  disabled?: boolean
  /** 화면마다 트리거 버튼 모양이 달라 호출부의 클래스를 그대로 쓴다. */
  triggerClassName?: string
  triggerTitle?: string
  iconSize?: number
}

export default function FilterMenu({
  ariaLabel,
  children,
  activeCount = 0,
  disabled = false,
  triggerClassName,
  triggerTitle = '필터',
  iconSize = 17,
}: FilterMenuProps) {
  const [isOpen, setIsOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen) return

    const closeOnOutside = (event: Event) => {
      const node = event.target instanceof Node ? event.target : null
      if (node && !wrapRef.current?.contains(node)) setIsOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false)
    }

    document.addEventListener('pointerdown', closeOnOutside)
    document.addEventListener('focusin', closeOnOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutside)
      document.removeEventListener('focusin', closeOnOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isOpen])

  return (
    <div className={styles.wrap} ref={wrapRef}>
      <button
        type="button"
        className={triggerClassName}
        disabled={disabled}
        title={triggerTitle}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        onClick={() => setIsOpen((open) => !open)}
      >
        <IconFilter size={iconSize} aria-hidden="true" />
        필터
        {activeCount > 0 && (
          <span className={styles.count}>
            <span className="sr-only">적용한 조건 </span>
            · {activeCount}
          </span>
        )}
      </button>
      {isOpen && !disabled && (
        <div className={styles.panel} role="dialog" aria-label={ariaLabel}>
          {children}
        </div>
      )}
    </div>
  )
}
