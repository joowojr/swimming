import { useState } from 'react'
import { IconCheck, IconLoader2, IconTrash } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import ChecklistCard from '../../components/ChecklistCard'
import TaskMenu, { TaskFlagMenuItems } from '../../components/TaskMenu'
import InlineEditableText from '../../components/InlineEditableText'
import type { DailyPlanItem } from '../plans/dailyPlanTypes'
import { ensureTodayPlanItem } from '../plans/todayPlan'
import CreateSessionModal from '../sessions/CreateSessionModal'
import TaskInfoModal from './TaskInfoModal'
import { updateTaskStatus, updateTaskTitle } from './taskApi'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from './taskLabels'
import type { TaskResponse, TaskStatus, TaskSummaryResponse } from './taskTypes'
import { useTaskStore } from '../../store/taskStore'
import styles from './TaskList.module.css'

interface TaskListItem extends TaskSummaryResponse {
  folderId?: number | null
}

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
  getMetaText,
}: TaskListProps) {
  const navigate = useNavigate()
  const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
  const [updateError, setUpdateError] = useState<{ taskId: number; message: string } | null>(null)
  const [sessionDraft, setSessionDraft] = useState<{ taskId: number; todayTasks: DailyPlanItem[] } | null>(null)
  const [moveTarget, setMoveTarget] = useState<TaskListItem | null>(null)
  const tasksById = useTaskStore((state) => state.byId)
  const upsertTasks = useTaskStore((state) => state.upsert)

  // 목록은 부모가 내려주지만 최신 값은 taskStore가 갖는다. 다른 화면에서 고친 것도 여기 반영된다.
  const applyTaskFlagOverride = (task: TaskListItem): TaskListItem => ({
    ...task,
    ...tasksById[task.id],
  })

  const updateTaskFlags = (updatedTask: TaskResponse) => {
    upsertTasks([updatedTask])
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
                status={task.status}
                ariaLabel={`${task.title} ${task.status === 'DONE' ? '완료 취소' : '완료 처리'}`}
                disabled={isPending || isDeleteMode}
                onToggle={() => void changeTaskStatus(task, task.status === 'DONE' ? 'TODO' : 'DONE')}
                description={task.folderId != null && getMetaText ? getMetaText(task) : undefined}
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
                        ariaLabel={`${task.urgent ? '즉시 ' : ''}${task.priority ? '중요 ' : ''}Task 제목`}
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
                      disabled={isPending || isDeleteMode}
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
                      <TaskFlagMenuItems
                          disabled={isPending}
                          session={{ onStart: () => void startSession(task), isPending }}
                          onMove={() => setMoveTarget(task)}
                        />
                      </TaskMenu>
                    )}
                  </>
                )}
                variant={connected ? 'flat' : 'default'}
              />
            </li>
        )
      })}
      </ol>
      {moveTarget && (
        <TaskInfoModal
          taskId={moveTarget.id}
          taskTitle={moveTarget.title}
          currentFolderId={moveTarget.folderId ?? null}
          currentPriority={moveTarget.priority}
          currentUrgent={moveTarget.urgent}
          plan={{ date: '' }}
          onClose={() => setMoveTarget(null)}
        />
      )}
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
