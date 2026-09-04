import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { IconDots, IconLoader2, IconPencil, IconPlayerPlay } from '@tabler/icons-react'
import DeleteIconButton from './DeleteIconButton'
import ModalTriggerButton from './ModalTriggerButton'
import styles from './TaskMenu.module.css'

interface TaskFlagMenuItemsProps {
  priority?: boolean
  urgent?: boolean
  disabled?: boolean
  onTogglePriority?: () => void
  onToggleUrgent?: () => void
  /** 다이브 세션 항목. dialogId를 주면 모달 트리거로, 아니면 바로 시작하는 버튼으로 그린다. */
  session?: {
    onStart: () => void
    isPending?: boolean
    dialogId?: string
    isOpen?: boolean
  }
  /** 수정 항목. 날짜·폴더·표시를 모달에서 함께 고친다. */
  onMove?: () => void
  /** 삭제 항목. 문구는 모든 화면에서 '삭제하기'로 통일한다. */
  onDelete?: () => void
}

/** 역할: TaskMenu를 쓰는 화면들이 공통으로 두는 중요·즉시·다이브 세션·삭제 항목을 한곳에서 정의한다. */
export function TaskFlagMenuItems({
  priority = false,
  urgent = false,
  disabled = false,
  onTogglePriority,
  onToggleUrgent,
  session,
  onMove,
  onDelete,
}: TaskFlagMenuItemsProps) {
  return (
    <>
      {onTogglePriority && (
        <button type="button" disabled={disabled} onClick={onTogglePriority}>
          {priority ? '📌  중요 해제' : '📌  중요 설정'}
        </button>
      )}
      {onToggleUrgent && (
        <button type="button" disabled={disabled} onClick={onToggleUrgent}>
          {urgent ? '⚡  ️즉시 해제' : '⚡  ️즉시 설정'}
        </button>
      )}
      {session && (session.dialogId
        ? (
          <ModalTriggerButton
            dialogId={session.dialogId}
            isOpen={session.isOpen}
            variant="plain"
            disabled={disabled}
            icon={<IconPlayerPlay size={14} aria-hidden="true" />}
            onClick={session.onStart}
          >
            다이브 세션
          </ModalTriggerButton>
        )
        : (
          <button type="button" disabled={disabled || session.isPending} onClick={session.onStart}>
            {session.isPending
              ? <IconLoader2 className={styles.spinner} size={14} aria-hidden="true" />
              : <IconPlayerPlay size={14} aria-hidden="true" />}
            다이브 세션
          </button>
        ))}
      {onMove && (
        <button type="button" disabled={disabled} onClick={onMove}>
          <IconPencil size={14} aria-hidden="true" />
          수정하기
        </button>
      )}
      {onDelete && (
        <DeleteIconButton label="삭제하기" iconSize={14} disabled={disabled} onClick={onDelete}>
          삭제하기
        </DeleteIconButton>
      )}
    </>
  )
}

interface TaskMenuProps {
  label: string
  children: ReactNode
  inline?: boolean
}

interface MenuPosition {
  top: number
  left: number
  visible: boolean
}

const MENU_GAP_PX = 4
const VIEWPORT_PADDING_PX = 8

export default function TaskMenu({
  label,
  children,
  inline = false,
}: TaskMenuProps) {
  const menuId = useId()
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const [isOpen, setIsOpen] = useState(false)
  const [position, setPosition] = useState<MenuPosition>({
    top: 0,
    left: 0,
    visible: false,
  })

  const close = useCallback((restoreFocus = false) => {
    setIsOpen(false)
    setPosition((current) => ({ ...current, visible: false }))
    if (restoreFocus) triggerRef.current?.focus()
  }, [])

  const updatePosition = useCallback(() => {
    const trigger = triggerRef.current
    const menu = menuRef.current
    if (!trigger || !menu) return

    const triggerRect = trigger.getBoundingClientRect()
    const menuRect = menu.getBoundingClientRect()
    const maxLeft = Math.max(
      VIEWPORT_PADDING_PX,
      window.innerWidth - menuRect.width - VIEWPORT_PADDING_PX,
    )
    const left = Math.min(
      Math.max(VIEWPORT_PADDING_PX, triggerRect.right - menuRect.width),
      maxLeft,
    )
    const spaceBelow = window.innerHeight - triggerRect.bottom
    const top = spaceBelow >= menuRect.height + MENU_GAP_PX + VIEWPORT_PADDING_PX
      ? triggerRect.bottom + MENU_GAP_PX
      : Math.max(
        VIEWPORT_PADDING_PX,
        triggerRect.top - menuRect.height - MENU_GAP_PX,
      )

    setPosition({ top, left, visible: true })
  }, [])

  useLayoutEffect(() => {
    if (!isOpen) return
    updatePosition()
    menuRef.current
      ?.querySelector<HTMLButtonElement>('button:not(:disabled)')
      ?.focus()
  }, [isOpen, updatePosition])

  useEffect(() => {
    if (!isOpen) return

    const isInsideMenu = (target: EventTarget | null) => {
      const node = target instanceof Node ? target : null
      return Boolean(
        node
        && (triggerRef.current?.contains(node) || menuRef.current?.contains(node)),
      )
    }
    const handlePointerDown = (event: PointerEvent) => {
      if (!isInsideMenu(event.target)) close()
    }
    const handleFocusIn = (event: FocusEvent) => {
      if (!isInsideMenu(event.target)) close()
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      close(true)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('focusin', handleFocusIn)
    document.addEventListener('keydown', handleKeyDown)
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('focusin', handleFocusIn)
      document.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [close, isOpen, updatePosition])

  return (
    <div className={`${styles['card-menu']} ${inline ? styles['card-menu-inline'] : ''}`}>
      <button
        ref={triggerRef}
        type="button"
        className={styles['card-menu-trigger']}
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-controls={isOpen ? menuId : undefined}
        onClick={() => setIsOpen((open) => !open)}
      >
        <IconDots size={15} aria-hidden="true" />
      </button>
      {isOpen && createPortal(
        <div
          ref={menuRef}
          id={menuId}
          className={styles['floating-card-menu']}
          role="menu"
          aria-label={label}
          style={{
            top: position.top,
            left: position.left,
            visibility: position.visible ? 'visible' : 'hidden',
          }}
          onClick={(event) => {
            if ((event.target as HTMLElement).closest('button')) close()
          }}
        >
          {children}
        </div>,
        document.body,
      )}
    </div>
  )
}
