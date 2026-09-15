import { useRef, useState } from 'react'
import type {
  CSSProperties,
  KeyboardEvent as ReactKeyboardEvent,
  PointerEvent as ReactPointerEvent,
  ReactNode,
} from 'react'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react'
import {
  MEMO_PANEL_MAX_WIDTH,
  MEMO_PANEL_MIN_WIDTH,
  useMemoPanelStore,
} from '../store/memoPanelStore'
import styles from './MemoSplitLayout.module.css'

/**
 * 역할: 왼쪽 본문과 오른쪽 메모를 좌우로 나누고, 메모 영역을 접거나 너비를 끌어 조절하게 한다.
 * 접힘 상태와 너비는 memoPanelStore가 소유하므로 이 컴포넌트를 쓰는 화면끼리 값을 공유한다.
 */
interface MemoSplitLayoutProps {
  children: ReactNode
  memo: ReactNode
  className?: string
}

const KEYBOARD_STEP = 16

export default function MemoSplitLayout({ children, memo, className }: MemoSplitLayoutProps) {
  const isOpen = useMemoPanelStore((state) => state.isOpen)
  const storedWidth = useMemoPanelStore((state) => state.width)
  const toggle = useMemoPanelStore((state) => state.toggle)
  const setWidth = useMemoPanelStore((state) => state.setWidth)

  // 끄는 동안에는 화면에만 반영하고, 손을 뗄 때 한 번만 저장한다.
  const [draggingWidth, setDraggingWidth] = useState<number | null>(null)
  const dragOriginRef = useRef<{ pointerX: number; width: number } | null>(null)

  const width = draggingWidth ?? storedWidth

  const clamp = (value: number) =>
    Math.min(MEMO_PANEL_MAX_WIDTH, Math.max(MEMO_PANEL_MIN_WIDTH, Math.round(value)))

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return
    dragOriginRef.current = { pointerX: event.clientX, width: storedWidth }
    setDraggingWidth(storedWidth)
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const origin = dragOriginRef.current
    if (!origin) return
    // 메모가 오른쪽에 있으므로 왼쪽으로 끌수록 넓어진다.
    setDraggingWidth(clamp(origin.width - (event.clientX - origin.pointerX)))
  }

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!dragOriginRef.current) return
    dragOriginRef.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    setDraggingWidth((current) => {
      if (current !== null) setWidth(current)
      return null
    })
  }

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowLeft') setWidth(storedWidth + KEYBOARD_STEP)
    else if (event.key === 'ArrowRight') setWidth(storedWidth - KEYBOARD_STEP)
    else if (event.key === 'Home') setWidth(MEMO_PANEL_MAX_WIDTH)
    else if (event.key === 'End') setWidth(MEMO_PANEL_MIN_WIDTH)
    else return
    event.preventDefault()
  }

  return (
    <div
      className={className ? `${styles.layout} ${className}` : styles.layout}
      style={{ '--memo-panel-width': `${width}px` } as CSSProperties}
      data-open={isOpen ? 'true' : 'false'}
      data-dragging={draggingWidth !== null ? 'true' : 'false'}
    >
      <div className={styles.main}>{children}</div>

      <div className={styles.gutter}>
        {isOpen && (
          <div
            className={styles.resizer}
            role="separator"
            aria-orientation="vertical"
            aria-label="메모 영역 너비 조절"
            aria-valuenow={width}
            aria-valuemin={MEMO_PANEL_MIN_WIDTH}
            aria-valuemax={MEMO_PANEL_MAX_WIDTH}
            tabIndex={0}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={endDrag}
            onPointerCancel={endDrag}
            onKeyDown={handleKeyDown}
          />
        )}
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={isOpen}
          aria-label={isOpen ? '메모 접기' : '메모 펼치기'}
          onClick={toggle}
        >
          {isOpen
            ? <IconChevronRight size={16} stroke={1.8} aria-hidden="true" />
            : <IconChevronLeft size={16} stroke={1.8} aria-hidden="true" />}
        </button>
      </div>

      <aside className={styles.panel} aria-label="메모" hidden={!isOpen}>
        <div className={styles['panel-inner']}>{memo}</div>
      </aside>
    </div>
  )
}
