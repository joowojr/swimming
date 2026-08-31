import { useState } from 'react'
import { IconCheck, IconFolder, IconLoader2, IconPlayerPause, IconPlayerPlay, IconTrash } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import ChecklistCard from '../../components/ChecklistCard'
import TaskMenu from '../../components/TaskMenu'
import InlineEditableText from '../../components/InlineEditableText'
import type { DailyPlanItem } from '../plans/dailyPlanTypes'
import { ensureTodayPlanItem } from '../plans/todayPlan'
import CreateSessionModal from '../sessions/CreateSessionModal'
import { updateTaskPriority, updateTaskStatus, updateTaskTitle, updateTaskUrgent } from '../tasks/taskApi'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from '../tasks/taskLabels'
import type { TaskResponse, TaskStatus, TaskSummaryResponse } from '../tasks/taskTypes'
import styles from './TaskList.module.css'

interface TaskListItem extends TaskSummaryResponse {
  projectId?: number | null
}

type TaskFlagOverride = Pick<TaskResponse, 'status' | 'priority' | 'urgent'>

interface TaskListProps {
  tasks: TaskListItem[]
  emptyTitle?: string
  emptyDescription?: string
  connected?: boolean
  isDeleteMode?: boolean
  selectedTaskIds?: ReadonlySet<number>
  isDeleting?: boolean
  onTaskSelectionChange?: (taskId: number) => void
  onTaskUpdated?: () => void
  getMetaText?: (task: TaskListItem) => string
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null
}

// function getTaskMeta(status: TaskStatus, sessionCount: number) {
//   if (status === 'DOING') return `${sessionCount}회 세션을 진행했어요`
//   if (status === 'TODO') return '언제든 편할 때 시작해요'
//   if (status === 'DONE') return '완료했습니다'
//   if (status === 'HOLD') return '편할 때 다시 시작해요'
//   return '집중을 이어가고 있어요'
// }

export default function TaskList({
  tasks,
  emptyTitle = '등록된 할 일이 없어요.',
  emptyDescription = '할 일이 추가되면 진행 순서대로 이곳에 표시됩니다.',
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
  const [taskFlagOverrides, setTaskFlagOverrides] = useState<Record<number, TaskFlagOverride>>({})

  const applyTaskFlagOverride = (task: TaskListItem): TaskListItem => ({
    ...task,
    ...taskFlagOverrides[task.id],
  })

  const updateTaskFlags = (updatedTask: TaskResponse) => {
    setTaskFlagOverrides((current) => ({
      ...current,
      [updatedTask.id]: {
        status: updatedTask.status,
        priority: updatedTask.priority,
        urgent: updatedTask.urgent,
      },
    }))
  }

  const changeTaskStatus = async (task: TaskSummaryResponse, status: TaskStatus) => {
    setPendingTaskId(task.id)
    setUpdateError(null)

    try {
      updateTaskFlags(await updateTaskStatus(task.id, { status }))
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      const message = apiMessage ?? '할 일상태를 변경하지 못했습니다. 다시 시도해 주세요.'
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
      await updateTaskTitle(task.id, { title })
      onTaskUpdated?.()
    } finally {
      setPendingTaskId(null)
    }
  }

  const changeTaskPriority = async (task: TaskListItem) => {
    setPendingTaskId(task.id)
    setUpdateError(null)
    try {
      updateTaskFlags(await updateTaskPriority(task.id, { priority: !task.priority }))
    } catch (error: unknown) {
      const message = isApiError(error) && error.message
        ? error.message
        : '우선 표시를 변경하지 못했습니다.'
      setUpdateError({ taskId: task.id, message })
    } finally {
      setPendingTaskId(null)
    }
  }

  const changeTaskUrgent = async (task: TaskListItem) => {
    setPendingTaskId(task.id)
    setUpdateError(null)
    try {
      updateTaskFlags(await updateTaskUrgent(task.id, { urgent: !task.urgent }))
    } catch (error: unknown) {
      const message = isApiError(error) && error.message
        ? error.message
        : '긴급 표시를 변경하지 못했습니다.'
      setUpdateError({ taskId: task.id, message })
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
      ?? '할 일제목을 저장하지 못했습니다.'
  }

  if (tasks.length === 0) {
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
      {tasks.map(applyTaskFlagOverride).map((task) => {
        const isPending = pendingTaskId === task.id
        const isSelected = selectedTaskIds.has(task.id)

        return (
            <li
              className={styles[`is-${task.status.toLowerCase()}`]}
              aria-busy={isPending}
              key={task.id}
            >
              <ChecklistCard
                leadingControl={(
                  <span className={styles.check} aria-hidden="true">
                    {task.status === 'DONE' ? <IconCheck size={16} stroke={2.2}/> : null}
                    {task.status === 'HOLD' ? <IconPlayerPause size={14} stroke={2}/> : null}
                    {task.status === 'DOING' ? <span className={styles['check-core']}/> : null}
                  </span>
                )}
                title={(
                  <div className={styles.content}>
                    <h3>
                      <InlineEditableText
                        className={[
                          styles['task-title'],
                          task.priority && styles['is-priority'],
                          task.urgent && styles['is-urgent'],
                        ].filter(Boolean).join(' ')}
                        value={task.title}
                        ariaLabel={`${task.priority ? '우선 ' : ''}${task.urgent ? '긴급 ' : ''}Task 제목`}
                        maxLength={255}
                        requiredMessage="할 일 제목을 입력해 주세요."
                        disabled={isPending || isDeleteMode}
                        onSave={(title) => changeTaskTitle(task, title)}
                        getErrorMessage={getTaskTitleError}
                      />
                    </h3>
                    {updateError?.taskId === task.id && (
                      <p className={styles.error} role="alert">{updateError.message}</p>
                    )}
                  </div>
                )}
                actions={(
                  <>
                    <select
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
                    {isDeleteMode ? (
                      <span className={`${styles['play-control']} ${styles['delete-control']}`}>
                        <button
                          type="button"
                          disabled={isPending || isDeleting}
                          aria-pressed={isSelected}
                          aria-describedby={`task-${task.id}-action-tooltip`}
                          onClick={() => onTaskSelectionChange?.(task.id)}
                        >
                          {isPending || (isDeleting && isSelected)
                            ? <IconLoader2 className={styles.spinner} size={16} aria-hidden="true"/>
                            : isSelected
                              ? <IconCheck size={16} stroke={2.2} aria-hidden="true"/>
                              : <IconTrash size={16} stroke={2} aria-hidden="true"/>}
                          <span className="sr-only">
                            {isSelected ? '삭제 선택 해제' : '삭제 선택'}
                          </span>
                        </button>
                        <span className={styles.tooltip} id={`task-${task.id}-action-tooltip`} role="tooltip">
                          {isSelected ? '선택 해제' : '삭제 선택'}
                        </span>
                      </span>
                    ) : (
                      <TaskMenu inline label={`${task.title} 카드 메뉴`}>
                      <>
                        <button
                        type="button"
                        disabled={isPending}
                        onClick={() => void changeTaskPriority(task)}
                      >
                        {task.priority ? '우선 해제' : '우선 설정'}
                      </button>
                      <button
                        type="button"
                        disabled={isPending}
                        onClick={() => void changeTaskUrgent(task)}
                      >
                          {task.urgent ? '긴급 해제' : '긴급 설정'}
                        </button>
                      </>
                        <button
                          type="button"
                          disabled={isPending}
                          onClick={() => void startSession(task)}
                        >
                          {isPending
                            ? <IconLoader2 className={styles.spinner} size={15} aria-hidden="true"/>
                            : <IconPlayerPlay size={15} aria-hidden="true"/>}
                          다이브 세션
                        </button>
                        <button
                          type="button"
                          disabled
                          title="폴더 이동 · 준비 중"
                          aria-label={`${task.title} 다른 폴더로 이동 · 준비 중`}
                        >
                          <IconFolder size={15} aria-hidden="true"/>
                          이동하기
                        </button>
                      </TaskMenu>
                    )}
                  </>
                )}
              />
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
