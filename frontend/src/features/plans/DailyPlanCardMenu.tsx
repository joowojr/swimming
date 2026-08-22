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
import { IconDots } from '@tabler/icons-react'
import styles from './DailyPlanSection.module.css'

interface DailyPlanCardMenuProps {
  label: string
  children: ReactNode
}

interface MenuPosition {
  top: number
  left: number
  visible: boolean
}

const MENU_GAP_PX = 4
const VIEWPORT_PADDING_PX = 8

export default function DailyPlanCardMenu({
  label,
  children,
}: DailyPlanCardMenuProps) {
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
    <div className={styles['card-menu']}>
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
        <IconDots size={17} aria-hidden="true" />
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
