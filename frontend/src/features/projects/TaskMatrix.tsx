import { useEffect, useMemo, useState } from 'react'
import { IconCheck, IconFolder, IconLoader2, IconPlayerPause, IconPlayerPlay } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import ChecklistCard from '../../components/ChecklistCard'
import InlineEditableText from '../../components/InlineEditableText'
import TaskMenu from '../../components/TaskMenu'
import type { DailyPlanItem } from '../plans/dailyPlanTypes'
import { ensureTodayPlanItem } from '../plans/todayPlan'
import CreateSessionModal from '../sessions/CreateSessionModal'
import TaskPickerModal from '../plans/TaskPickerModal'
import type { Project } from './projectTypes'
import { createTaskWithOptionalPlan } from '../tasks/taskApi'
import { getTaskList, updateTaskStatus, updateTaskTitle } from '../tasks/taskApi'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from '../tasks/taskLabels'
import type { TaskResponse, TaskStatus } from '../tasks/taskTypes'
import styles from './TaskMatrix.module.css'

type MatrixStatus = 'loading' | 'ready' | 'error'

interface MatrixSection {
  id: string
  title: string
  matches: (task: TaskResponse) => boolean
}

const matrixSections: MatrixSection[] = [
  {
    id: 'priority-urgent',
    title: '⚡️ 즉시 · 📌 중요',
    matches: (task) => task.priority && task.urgent,
  },
  {
    id: 'urgent',
    title: '⚡️ 즉시',
    matches: (task) => !task.priority && task.urgent,
  },
  {
    id: 'priority',
    title: '📌 중요',
    matches: (task) => task.priority && !task.urgent,
  },
  {
    id: 'standard',
    title: '일반',
    matches: (task) => !task.priority && !task.urgent,
  },
]

export default function TaskMatrix({ projects }: { projects: Project[] }) {
  const navigate = useNavigate()
  const [tasks, setTasks] = useState<TaskResponse[]>([])
  const [status, setStatus] = useState<MatrixStatus>('loading')
  const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
  const [updateError, setUpdateError] = useState<{ taskId: number; message: string } | null>(null)
  const [sessionDraft, setSessionDraft] = useState<{ taskId: number; todayTasks: DailyPlanItem[] } | null>(null)
  const [addDraft, setAddDraft] = useState<{ priority: boolean; urgent: boolean } | null>(null)

  const loadTasks = async () => {
    try {
      setTasks(await getTaskList('all'))
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }

  const retryLoad = () => {
    setStatus('loading')
    void loadTasks()
  }

  const replaceTask = (updatedTask: TaskResponse) => {
    setTasks((current) => current.map((task) => task.id === updatedTask.id ? updatedTask : task))
  }

  const changeTaskTitle = async (task: TaskResponse, title: string) => {
    setPendingTaskId(task.id)
    setUpdateError(null)
    try {
      replaceTask(await updateTaskTitle(task.id, { title }))
    } finally {
      setPendingTaskId(null)
    }
  }

  const changeTaskStatus = async (task: TaskResponse, nextStatus: TaskStatus) => {
    setPendingTaskId(task.id)
    setUpdateError(null)
    try {
      replaceTask(await updateTaskStatus(task.id, { status: nextStatus }))
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setUpdateError({
        taskId: task.id,
        message: apiMessage ?? 'Task 상태를 변경하지 못했습니다. 다시 시도해 주세요.',
      })
    } finally {
      setPendingTaskId(null)
    }
  }

  const startSession = async (task: TaskResponse) => {
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

  const getTaskTitleError = (error: unknown) => {
    const apiError = typeof error === 'object' && error !== null
      ? error as ApiError
      : undefined
    return apiError?.errors?.title
      ?? apiError?.message
      ?? 'Task 제목을 저장하지 못했습니다.'
  }

  useEffect(() => {
    const controller = new AbortController()
    void getTaskList('all', controller.signal)
      .then((nextTasks) => {
        setTasks(nextTasks)
        setStatus('ready')
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setStatus('error')
      })
    return () => controller.abort()
  }, [])

  const groupedTasks = useMemo(
    () => matrixSections.map((section) => ({
      ...section,
      tasks: tasks.filter(section.matches),
    })),
    [tasks],
  )

  return (
    <section className={styles.matrix} aria-labelledby="task-matrix-title">

      {status === 'loading' ? (
        <div className={styles.state} role="status">
          <IconLoader2 className={styles.spinner} size={19} aria-hidden="true" />
          Task를 불러오는 중…
        </div>
      ) : status === 'error' ? (
        <div className={styles.state}>
          <p>Task를 불러오지 못했습니다.</p>
          <button type="button" onClick={retryLoad}>다시 불러오기</button>
        </div>
      ) : (
        <div className={styles.grid}>
          {groupedTasks.map((section) => (
            <section className={styles.quadrant} aria-labelledby={`${section.id}-title`} key={section.id}>
              <header className={styles['quadrant-header']}>
                <h3 id={`${section.id}-title`}>{section.title}</h3>
                <div className={styles['quadrant-actions']}>
                  <span className={styles.count}>{section.tasks.length}개</span>
                  <button
                    type="button"
                    className={styles['add-button']}
                    aria-label={`${section.title} 영역에 할 일 추가`}
                    onClick={() => setAddDraft({
                      priority: section.id === 'priority' || section.id === 'priority-urgent',
                      urgent: section.id === 'urgent' || section.id === 'priority-urgent',
                    })}
                  >+</button>
                </div>
              </header>
              {section.tasks.length === 0 ? (
                <p className={styles.empty}>이 영역에는 Task가 없습니다.</p>
              ) : (
                <ol className={styles.list}>
                  {section.tasks.map((task) => {
                    const isPending = pendingTaskId === task.id
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
                              <h4>
                                <InlineEditableText
                                  className={styles['task-title']}
                                  value={task.title}
                                  ariaLabel={`${task.urgent ? '즉시 ' : ''}${task.priority ? '중요 ' : ''}Task 제목`}
                                  maxLength={255}
                                  requiredMessage="Task 제목을 입력해 주세요."
                                  disabled={isPending}
                                  onSave={(title) => changeTaskTitle(task, title)}
                                  getErrorMessage={getTaskTitleError}
                                />
                              </h4>
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
                                {TASK_STATUS_VALUES.map((taskStatus) => (
                                  <option value={taskStatus} key={taskStatus}>{TASK_STATUS_LABEL[taskStatus]}</option>
                                ))}
                              </select>
                              <TaskMenu inline label={`${task.title} 카드 메뉴`}>
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
                            </>
                          )}
                        />
                      </li>
                    )
                  })}
                </ol>
              )}
            </section>
          ))}
        </div>
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
      {addDraft && (
        <TaskPickerModal
          projects={projects}
          selectedTaskIds={new Set()}
          initialPriority={addDraft.priority}
          initialUrgent={addDraft.urgent}
          onAdd={async () => undefined}
          onAddTask={async (title, projectId, priority, urgent, planDate) => {
            await createTaskWithOptionalPlan({
              title,
              priority,
              urgent,
              projectId,
              planDate,
            })
            await loadTasks()
          }}
          onClose={() => setAddDraft(null)}
        />
      )}
    </section>
  )
}
