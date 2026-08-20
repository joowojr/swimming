import { useRef, useState } from 'react'
import { IconCheck, IconLoader2, IconPlayerPause, IconPlayerPlay } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import { updateTask } from '../tasks/taskApi'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from '../tasks/taskLabels'
import type { TaskStatus, TaskSummaryResponse } from '../tasks/taskTypes'
import styles from './TaskList.module.css'

interface TaskListProps {
  tasks: TaskSummaryResponse[]
  emptyTitle?: string
  emptyDescription?: string
  connected?: boolean
  onTaskUpdated?: () => void
}

function clampCompletionPct(value: number) {
  return Math.min(100, Math.max(0, value))
}

function getTaskMeta(status: TaskStatus, completionPct: number) {
  if (status === 'TODO') return '언제든 편할 때 시작해요'
  if (status === 'DONE') return `${completionPct}% 완료`
  if (status === 'HOLD') return `${completionPct}%까지 진행했어요`
  return `${completionPct}% 진행`
}

export default function TaskList({
  tasks,
  emptyTitle = '등록된 task가 없습니다.',
  emptyDescription = 'task가 추가되면 진행 순서대로 이곳에 표시됩니다.',
  connected = false,
  onTaskUpdated,
}: TaskListProps) {
  const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
  const [updateError, setUpdateError] = useState<{ taskId: number; message: string } | null>(null)
  const statusSelectRefs = useRef(new Map<number, HTMLSelectElement>())
  const orderedTasks = [...tasks].sort((a, b) => a.orderIdx - b.orderIdx)

  const changeTaskStatus = async (task: TaskSummaryResponse, status: TaskStatus) => {
    setPendingTaskId(task.id)
    setUpdateError(null)

    try {
      await updateTask(task.id, {
        title: task.title,
        status,
        completionPct: status === 'DONE' ? 100 : task.completionPct,
      })
      onTaskUpdated?.()
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      const message = apiMessage ?? 'task 상태를 변경하지 못했습니다. 다시 시도해 주세요.'
      setUpdateError({ taskId: task.id, message })
    } finally {
      setPendingTaskId(null)
    }
  }

  const openStatusPicker = (taskId: number) => {
    const select = statusSelectRefs.current.get(taskId)
    if (!select || select.disabled) return

    select.focus()
    try {
      select.showPicker()
    } catch {
      select.focus()
    }
  }

  if (orderedTasks.length === 0) {
    return (
      <div className={`${styles.empty} ${connected ? styles.connected : ''}`}>
        <span className={styles['empty-node']} aria-hidden="true" />
        <h3>{emptyTitle}</h3>
        <p>{emptyDescription}</p>
      </div>
    )
  }

  return (
    <ol className={`${styles.list} ${connected ? styles.connected : ''}`}>
      {orderedTasks.map((task) => {
        const completionPct = clampCompletionPct(task.completionPct)
        const isPending = pendingTaskId === task.id

        return (
          <li
            className={`${styles.item} ${styles[`is-${task.status.toLowerCase()}`]}`}
            aria-busy={isPending}
            key={task.id}
          >
            <button
              type="button"
              className={styles.node}
              aria-label={`${task.title} 상태 변경`}
              disabled={isPending}
              onClick={() => openStatusPicker(task.id)}
            >
              {task.status === 'DONE' ? <IconCheck size={16} stroke={2.2} /> : null}
              {task.status === 'HOLD' ? <IconPlayerPause size={14} stroke={2} /> : null}
              {task.status === 'DOING' ? <span className={styles['node-core']} /> : null}
            </button>
            <div className={styles.content}>
              <div className={styles.heading}>
                <h3>{task.title}</h3>
                <select
                  ref={(element) => {
                    if (element) statusSelectRefs.current.set(task.id, element)
                    else statusSelectRefs.current.delete(task.id)
                  }}
                  className={styles.status}
                  value={task.status}
                  aria-label={`${task.title} 상태`}
                  disabled={isPending}
                  onChange={(event) => void changeTaskStatus(task, event.target.value as TaskStatus)}
                >
                  {TASK_STATUS_VALUES.map((status) => (
                    <option value={status} key={status}>{TASK_STATUS_LABEL[status]}</option>
                  ))}
                </select>
              </div>
              <p className={styles.meta}>{getTaskMeta(task.status, completionPct)}</p>
              {updateError?.taskId === task.id && (
                <p className={styles.error} role="alert">{updateError.message}</p>
              )}
            </div>
            <span className={styles['play-control']}>
              <button
                type="button"
                disabled={isPending}
                aria-describedby={`task-${task.id}-play-tooltip`}
                onClick={() => void changeTaskStatus(task, 'DOING')}
              >
                {isPending
                  ? <IconLoader2 className={styles.spinner} size={16} aria-hidden="true" />
                  : <IconPlayerPlay size={16} stroke={2} aria-hidden="true" />}
                <span className="sr-only">세션 시작</span>
              </button>
              <span className={styles.tooltip} id={`task-${task.id}-play-tooltip`} role="tooltip">
                세션 시작
              </span>
            </span>
          </li>
        )
      })}
    </ol>
  )
}
