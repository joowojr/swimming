import { useRef, useState } from 'react'
import { IconCheck, IconLoader2, IconPlayerPause, IconPlayerPlay, IconTrash } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import InlineEditableText from '../../components/InlineEditableText'
import type { DailyPlanItem } from '../plans/dailyPlanTypes'
import { ensureTodayPlanItem } from '../plans/todayPlan'
import CreateSessionModal from '../sessions/CreateSessionModal'
import { updateTask } from '../tasks/taskApi'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from '../tasks/taskLabels'
import type { TaskStatus, TaskSummaryResponse } from '../tasks/taskTypes'
import styles from './TaskList.module.css'

interface TaskListProps {
  tasks: TaskSummaryResponse[]
  emptyTitle?: string
  emptyDescription?: string
  connected?: boolean
  isDeleteMode?: boolean
  selectedTaskIds?: ReadonlySet<number>
  isDeleting?: boolean
  onTaskSelectionChange?: (taskId: number) => void
  onTaskUpdated?: () => void
}

// function clampCompletionPct(value: number) {
//   return Math.min(100, Math.max(0, value))
// }

function getTaskMeta(status: TaskStatus) {
  if (status === 'TODO') return '언제든 편할 때 시작해요'
  if (status === 'DONE') return `완료했습니다`
  if (status === 'HOLD') return `편할 때 다시 시작해요`
  return `잘 하고 있어요`
}

export default function TaskList({
  tasks,
  emptyTitle = '등록된 할 일이 없어요.',
  emptyDescription = 'task가 추가되면 진행 순서대로 이곳에 표시됩니다.',
  connected = false,
  isDeleteMode = false,
  selectedTaskIds = new Set<number>(),
  isDeleting = false,
  onTaskSelectionChange,
  onTaskUpdated,
}: TaskListProps) {
  const navigate = useNavigate()
  const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
  const [updateError, setUpdateError] = useState<{ taskId: number; message: string } | null>(null)
  const [sessionDraft, setSessionDraft] = useState<{ taskId: number; todayTasks: DailyPlanItem[] } | null>(null)
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

  const startSession = async (task: TaskSummaryResponse) => {
    setPendingTaskId(task.id)
    setUpdateError(null)

    try {
      setSessionDraft({ taskId: task.id, todayTasks: await ensureTodayPlanItem(task.id) })
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setUpdateError({
        taskId: task.id,
        message: apiMessage ?? '세션을 준비하지 못했습니다. 다시 시도해 주세요.',
      })
    } finally {
      setPendingTaskId(null)
    }
  }

  const changeTaskTitle = async (task: TaskSummaryResponse, title: string) => {
    setPendingTaskId(task.id)
    setUpdateError(null)

    try {
      await updateTask(task.id, {
        title,
        status: task.status,
        completionPct: task.completionPct,
      })
      onTaskUpdated?.()
    } finally {
      setPendingTaskId(null)
    }
  }

  const getTaskTitleError = (error: unknown) => {
    const apiError = typeof error === 'object' && error !== null
      ? error as ApiError
      : undefined
    return apiError?.errors?.title
      ?? apiError?.message
      ?? 'Task 제목을 저장하지 못했습니다.'
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
    <>
      <ol className={`${styles.list} ${connected ? styles.connected : ''}`}>
      {orderedTasks.map((task) => {
        const isPending = pendingTaskId === task.id
        const isSelected = selectedTaskIds.has(task.id)

        return (
            <li
                className={`${styles.item} ${styles[`is-${task.status.toLowerCase()}`]}`}
                aria-busy={isPending}
                key={task.id}
            >
            <span
                className={styles.node}
                aria-hidden="true"
            >
            {/*<button*/}
              {/*  type="button"*/}
              {/*  className={styles.node}*/}
              {/*  aria-label={`${task.title} 상태 변경`}*/}
              {/*  disabled={isPending}*/}
              {/*  onClick={() => openStatusPicker(task.id)}*/}
              {/*>*/}
              {task.status === 'DONE' ? <IconCheck size={16} stroke={2.2}/> : null}
              {task.status === 'HOLD' ? <IconPlayerPause size={14} stroke={2}/> : null}
              {task.status === 'DOING' ? <span className={styles['node-core']}/> : null}
            </span>
              <div className={styles.content}>
                <div className={styles.heading}>
                  <h3>
                    <InlineEditableText
                        className={styles['task-title']}
                        value={task.title}
                        ariaLabel="Task 제목"
                        maxLength={255}
                        requiredMessage="Task 제목을 입력해 주세요."
                        disabled={isPending || isDeleteMode}
                        onSave={(title) => changeTaskTitle(task, title)}
                        getErrorMessage={getTaskTitleError}
                    />
                  </h3>
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
                <p className={styles.meta}>{getTaskMeta(task.status)}</p>
                {updateError?.taskId === task.id && (
                    <p className={styles.error} role="alert">{updateError.message}</p>
                )}
              </div>
              <span
                  className={`${styles['play-control']} ${isDeleteMode ? styles['delete-control'] : ''}`}
              >
              <button
                  type="button"
                  disabled={isPending || isDeleting}
                  aria-pressed={isDeleteMode ? isSelected : undefined}
                  aria-describedby={`task-${task.id}-action-tooltip`}
                  onClick={() => {
                    if (isDeleteMode) onTaskSelectionChange?.(task.id)
                    else void startSession(task)
                  }}
              >
                {isPending || (isDeleting && isSelected)
                    ? <IconLoader2 className={styles.spinner} size={16} aria-hidden="true"/>
                    : isDeleteMode
                        ? isSelected
                            ? <IconCheck size={16} stroke={2.2} aria-hidden="true"/>
                            : <IconTrash size={16} stroke={2} aria-hidden="true"/>
                        : <IconPlayerPlay size={16} stroke={2} aria-hidden="true"/>}
                <span className="sr-only">
                  {isDeleteMode ? (isSelected ? '삭제 선택 해제' : '삭제 선택') : '다이브 세션'}
                </span>
              </button>
              <span className={styles.tooltip} id={`task-${task.id}-action-tooltip`} role="tooltip">
                {isDeleteMode ? (isSelected ? '선택 해제' : '삭제 선택') : '다이브 세션'}
              </span>
            </span>
            </li>
        )
      })}
      </ol>
      {sessionDraft && (
        <CreateSessionModal
          todayTasks={sessionDraft.todayTasks}
          initialTaskId={sessionDraft.taskId}
          onClose={() => setSessionDraft(null)}
          onStarted={(session) => {
            setSessionDraft(null)
            navigate(`/sessions/${session.id}`)
          }}
        />
      )}
    </>
  )
}
