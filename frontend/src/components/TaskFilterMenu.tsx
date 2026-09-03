import { useEffect, useRef, useState } from 'react'
import { IconFilter } from '@tabler/icons-react'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from '../features/tasks/taskLabels'
import { EMPTY_TASK_FILTER, countActiveFilters } from '../features/tasks/taskFilter'
import type { TaskFilter } from '../features/tasks/taskFilter'
import styles from './TaskFilterMenu.module.css'

/** 역할: 할 일 목록을 상태·중요·즉시로 좁히는 필터 메뉴를 화면들이 같은 규칙으로 쓰게 한다. */
interface TaskFilterMenuProps {
  value: TaskFilter
  onChange: (filter: TaskFilter) => void
  disabled?: boolean
  /** 화면마다 트리거 버튼 모양이 달라 호출부의 클래스를 그대로 쓴다. */
  triggerClassName?: string
  triggerTitle?: string
  iconSize?: number
}

export default function TaskFilterMenu({
  value,
  onChange,
  disabled = false,
  triggerClassName,
  triggerTitle = '할 일 필터',
  iconSize = 17,
}: TaskFilterMenuProps) {
  const [isOpen, setIsOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)
  const activeCount = countActiveFilters(value)

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
        <div className={styles.panel} role="dialog" aria-label="할 일 필터">
          <label className={styles.field}>
            <span>상태</span>
            <select
              value={value.status}
              onChange={(event) => onChange({
                ...value,
                status: event.target.value as TaskFilter['status'],
              })}
            >
              <option value="ALL">전체</option>
              {TASK_STATUS_VALUES.map((status) => (
                <option value={status} key={status}>{TASK_STATUS_LABEL[status]}</option>
              ))}
            </select>
          </label>
          <label className={styles.check}>
            <input
              type="checkbox"
              checked={value.priority}
              onChange={(event) => onChange({ ...value, priority: event.target.checked })}
            />
            📌 중요
          </label>
          <label className={styles.check}>
            <input
              type="checkbox"
              checked={value.urgent}
              onChange={(event) => onChange({ ...value, urgent: event.target.checked })}
            />
            ⚡️ 즉시
          </label>
          <button
            type="button"
            className={styles.reset}
            disabled={activeCount === 0}
            onClick={() => onChange(EMPTY_TASK_FILTER)}
          >
            조건 지우기
          </button>
        </div>
      )}
    </div>
  )
}
