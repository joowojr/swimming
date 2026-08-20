import { IconCheck, IconPlayerPause, IconPlayerPlay } from '@tabler/icons-react'
import type { TaskStatus, TaskSummaryResponse } from '../tasks/taskTypes'
import styles from './TaskList.module.css'

interface TaskListProps {
  tasks: TaskSummaryResponse[]
  emptyTitle?: string
  emptyDescription?: string
  connected?: boolean
}

const statusLabel: Record<TaskStatus, string> = {
  TODO: '아직',
  DOING: '하는 중',
  DONE: '끝냄',
  HOLD: '잠시 멈춤',
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
}: TaskListProps) {
  const orderedTasks = [...tasks].sort((a, b) => a.orderIdx - b.orderIdx)

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

        return (
          <li className={`${styles.item} ${styles[`is-${task.status.toLowerCase()}`]}`} key={task.id}>
            <span className={styles.node} aria-hidden="true">
              {task.status === 'DONE' ? <IconCheck size={16} stroke={2.2} /> : null}
              {task.status === 'HOLD' ? <IconPlayerPause size={14} stroke={2} /> : null}
              {task.status === 'DOING' ? <span className={styles['node-core']} /> : null}
            </span>
            <div className={styles.content}>
              <div className={styles.heading}>
                <h3>{task.title}</h3>
                <span className={styles.status}>{statusLabel[task.status]}</span>
              </div>
              <p className={styles.meta}>{getTaskMeta(task.status, completionPct)}</p>
            </div>
            <span className={styles['play-control']}>
              <button type="button" disabled aria-describedby={`task-${task.id}-play-tooltip`}>
                <IconPlayerPlay size={16} stroke={2} aria-hidden="true" />
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
