import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { IconArrowNarrowRight, IconFilter } from '@tabler/icons-react'
import type { ColumnOrder, LayoutAxis, LayoutOptions, NodeSort } from './graphLayout'
import styles from './GraphLayoutMenu.module.css'

interface GraphLayoutMenuProps {
  value: Required<LayoutOptions>
  onChange: (options: Required<LayoutOptions>) => void
}

interface Choice<T extends string> {
  value: T
  label: ReactNode
}

const arrow = <IconArrowNarrowRight size={15} stroke={1.8} aria-hidden="true" />

const AXES: Choice<LayoutAxis>[] = [
  { value: 'horizontal', label: '좌우로 펼치기' },
  { value: 'vertical', label: '위아래로 쌓기' },
]

const ORDERS: Choice<ColumnOrder>[] = [
  { value: 'source-first', label: <>문서 {arrow} 개념</> },
  { value: 'subject-first', label: <>개념 {arrow} 문서</> },
]

const SORTS: Choice<NodeSort>[] = [
  { value: 'linked', label: '이어진 순' },
  { value: 'degree', label: '연결 많은 순' },
]

/**
 * 그래프를 어떻게 배치할지 고르는 메뉴.
 *
 * 배치는 한 번 정하면 계속 두고 보는 값이라, 늘 펼쳐 두는 토글 대신 할 일 필터와 같은
 * 트리거+패널 모양으로 접어 둔다.
 */
export default function GraphLayoutMenu({ value, onChange }: GraphLayoutMenuProps) {
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

  const renderGroup = <T extends string>(
    legend: string,
    name: string,
    choices: Choice<T>[],
    selected: T,
    apply: (next: T) => Required<LayoutOptions>,
  ) => (
    <fieldset className={styles.group}>
      <legend>{legend}</legend>
      {choices.map((choice) => (
        <label className={styles.choice} key={choice.value}>
          <input
            type="radio"
            name={name}
            value={choice.value}
            checked={selected === choice.value}
            onChange={() => onChange(apply(choice.value))}
          />
          <span>{choice.label}</span>
        </label>
      ))}
    </fieldset>
  )

  return (
    <div className={styles.wrap} ref={wrapRef}>
      <button
        type="button"
        className={styles.trigger}
        title="그래프 배치"
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        onClick={() => setIsOpen((open) => !open)}
      >
        <IconFilter size={15} stroke={1.8} aria-hidden="true" />
        보기
      </button>
      {isOpen && (
        <div className={styles.panel} role="dialog" aria-label="그래프 배치">
          {renderGroup('방향', 'graph-axis', AXES, value.axis,
            (axis) => ({ ...value, axis }))}
          {renderGroup('시작', 'graph-order', ORDERS, value.order,
            (order) => ({ ...value, order }))}
          {renderGroup('정렬', 'graph-sort', SORTS, value.sort,
            (sort) => ({ ...value, sort }))}
        </div>
      )}
    </div>
  )
}
