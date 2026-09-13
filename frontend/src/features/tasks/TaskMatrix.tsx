import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { IconLoader2 } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import AddItemButton from '../../components/AddItemButton'
import ChecklistCard from '../../components/ChecklistCard'
import FolderLink from '../../components/FolderLink'
import InlineEditableText from '../../components/InlineEditableText'
import TaskMenu, { TaskFlagMenuItems } from '../../components/TaskMenu'
import type { DailyPlanItem } from '../calendar/dailyPlanTypes'
import { ensureTodayPlanItem } from '../calendar/todayPlan'
import CreateSessionModal from '../sessions/CreateSessionModal'
import TaskInfoModal from './TaskInfoModal'
import TaskPickerModal from '../calendar/TaskPickerModal'
import { useFolderStore } from '../../store/folderStore.ts'
import { useDailyPlanStore } from '../../store/dailyPlanStore'
import { useTaskStore } from '../../store/taskStore'
import { createTaskWithOptionalPlan } from './taskApi'
import { deleteTasks, getTaskMatrixPage, moveTask, updateTaskStatus, updateTaskTitle } from './taskApi'
import type { TaskFilter } from './taskFilter'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from './taskLabels'
import type { TaskMatrixItem, TaskMatrixSection, TaskResponse, TaskStatus } from './taskTypes'
import styles from './TaskMatrix.module.css'

type MatrixStatus = 'loading' | 'ready' | 'error'

interface MatrixSection {
  id: string
  title: string
  apiSection: TaskMatrixSection
}

interface SectionState {
  items: TaskMatrixItem[]
  nextCursor: string | null
  hasNext: boolean
  status: MatrixStatus
}

const matrixSections: MatrixSection[] = [
  {
    id: 'priority-urgent',
    title: '⚡️ 즉시 · 📌 중요',
    apiSection: 'PRIORITY_URGENT',
  },
  {
    id: 'urgent',
    title: '⚡️ 즉시',
    apiSection: 'URGENT',
  },
  {
    id: 'priority',
    title: '📌 중요',
    apiSection: 'PRIORITY',
  },
  {
    id: 'standard',
    title: '일반',
    apiSection: 'STANDARD',
  },
]

const initialSectionState = (): SectionState => ({ items: [], nextCursor: null, hasNext: true, status: 'loading' })

function autoScrollDuringDrag(container: HTMLElement, clientY: number) {
  const bounds = container.getBoundingClientRect()
  const edgeSize = 56
  if (clientY < bounds.top + edgeSize) {
    container.scrollBy({ top: -16 })
  } else if (clientY > bounds.bottom - edgeSize) {
    container.scrollBy({ top: 16 })
  }
}

interface TaskMatrixProps {
  /** 상태 필터. 서버 조회 조건으로 넘어간다. 중요·즉시는 구간 자체라 필터로 받지 않는다. */
  statusFilter?: TaskFilter['status']
}

export default function TaskMatrix({ statusFilter = 'ALL' }: TaskMatrixProps) {
  const navigate = useNavigate()
  const folders = useFolderStore((state) => state.folders)
  const folderNameById = useMemo(
    () => new Map(folders.map((folder) => [folder.id, folder.name])),
    [folders],
  )
  const [sections, setSections] = useState<Record<string, SectionState>>(
    () => Object.fromEntries(matrixSections.map((section) => [section.id, initialSectionState()])),
  )
  const [status, setStatus] = useState<MatrixStatus>('loading')
  const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
  const [updateError, setUpdateError] = useState<{ taskId: number; message: string } | null>(null)
  const [sessionDraft, setSessionDraft] = useState<{ taskId: number; todayTasks: DailyPlanItem[] } | null>(null)
  const [moveTarget, setMoveTarget] = useState<TaskMatrixItem | null>(null)
  const [addDraft, setAddDraft] = useState<{ priority: boolean; urgent: boolean } | null>(null)
  const [dragState, setDragState] = useState<{ taskId: number; sourceSection: string } | null>(null)
  const [dropTarget, setDropTarget] = useState<{ sectionId: string; taskId: number | null } | null>(null)

  const loadingSections = useRef(new Set<string>())
  const sectionsRef = useRef(sections)
  // loadSection이 필터 때문에 새로 만들어지면 스크롤 핸들러까지 매번 바뀌므로 ref로 읽는다.
  // 아래 재조회 effect보다 먼저 선언해 두어야 최신 필터로 조회한다.
  const statusFilterRef = useRef(statusFilter)

  useEffect(() => {
    sectionsRef.current = sections
  }, [sections])

  useEffect(() => {
    statusFilterRef.current = statusFilter
  }, [statusFilter])

  const loadSection = useCallback(async (section: MatrixSection, reset = false, signal?: AbortSignal) => {
    const current = sectionsRef.current[section.id] ?? initialSectionState()
    if (!reset && (!current.hasNext || loadingSections.current.has(section.id))) return
    loadingSections.current.add(section.id)
    setSections((value) => ({ ...value, [section.id]: { ...value[section.id], status: 'loading' } }))
    try {
      if (!reset) await new Promise((resolve) => window.setTimeout(resolve, 400))
      const page = await getTaskMatrixPage(section.apiSection, {
        cursor: reset ? null : current.nextCursor,
        status: statusFilterRef.current === 'ALL' ? undefined : statusFilterRef.current,
        signal,
      })
      setSections((value) => {
        const previous = reset ? [] : (value[section.id]?.items ?? [])
        const seen = new Set(previous.map((task) => task.id))
        const items = [...previous, ...page.items.filter((task) => !seen.has(task.id))]
        return { ...value, [section.id]: { items, nextCursor: page.nextCursor, hasNext: page.hasNext, status: 'ready' } }
      })
    } catch {
      if (!signal?.aborted) setSections((value) => ({ ...value, [section.id]: { ...value[section.id], status: 'error' } }))
    } finally {
      loadingSections.current.delete(section.id)
    }
  }, [])

  const loadTasks = useCallback(async (signal?: AbortSignal) => {
    setStatus('loading')
    await Promise.all(matrixSections.map((section) => loadSection(section, true, signal)))
    if (!signal?.aborted) setStatus('ready')
  }, [loadSection])

  const retryLoad = () => {
    void loadTasks()
  }

  const tasksById = useTaskStore((state) => state.byId)
  const taskListRevision = useTaskStore((state) => state.listRevision)
  const upsertTasks = useTaskStore((state) => state.upsert)
  const removeTasks = useTaskStore((state) => state.remove)
  const invalidatePlanDate = useDailyPlanStore((state) => state.invalidateDate)

  const replaceTask = (updatedTask: TaskResponse) => {
    // 다른 화면도 같은 task를 보고 있으므로 단일 출처를 먼저 갱신한다.
    upsertTasks([updatedTask])
    setSections((current) => Object.fromEntries(Object.entries(current).map(([key, section]) => [
      key,
      { ...section, items: section.items.map((task) => task.id === updatedTask.id ? { ...task, ...updatedTask } : task) },
    ])))
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
        message: apiMessage ?? '상태를 변경하지 못했습니다. 다시 시도해 주세요.',
      })
    } finally {
      setPendingTaskId(null)
    }
  }

  const deleteTask = async (task: TaskResponse) => {
    setPendingTaskId(task.id)
    setUpdateError(null)
    try {
      await deleteTasks({ taskIds: [task.id] })
      removeTasks([task.id])
      setSections((current) => Object.fromEntries(Object.entries(current).map(([key, section]) => [
        key,
        { ...section, items: section.items.filter((item) => item.id !== task.id) },
      ])))
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
      setUpdateError({ taskId: task.id, message: apiMessage ?? '할 일을 삭제하지 못했습니다.' })
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
      ?? '제목을 저장하지 못했습니다.'
  }

  const dropTask = async (targetSection: MatrixSection, targetTaskId: number | null, insertBefore: boolean) => {
    if (!dragState || dragState.taskId === targetTaskId) return
    const sourceItems = sectionsRef.current[dragState.sourceSection]?.items ?? []
    const targetItems = (sectionsRef.current[targetSection.id]?.items ?? [])
      .filter((task) => task.id !== dragState.taskId)
    const targetIndex = targetTaskId === null
      ? targetItems.length
      : targetItems.findIndex((task) => task.id === targetTaskId) + (insertBefore ? 0 : 1)
    if (targetIndex < 0) return
    const sourceIndex = sourceItems.findIndex((task) => task.id === dragState.taskId)
    if (dragState.sourceSection === targetSection.id && sourceIndex >= 0 && targetIndex === sourceIndex) return
    setPendingTaskId(dragState.taskId)
    setUpdateError(null)
    try {
      await moveTask(dragState.taskId, {
        scope: 'MATRIX',
        targetSection: targetSection.apiSection,
        previousTaskId: targetItems[targetIndex - 1]?.id ?? null,
        nextTaskId: targetItems[targetIndex]?.id ?? null,
        ...(statusFilter === 'ALL' ? {} : { status: statusFilter }),
      })
      const sourceSection = matrixSections.find((section) => section.id === dragState.sourceSection)
      await Promise.all([
        sourceSection ? loadSection(sourceSection, true) : Promise.resolve(),
        loadSection(targetSection, true),
      ])
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
      setUpdateError({ taskId: dragState.taskId, message: apiMessage ?? '할 일을 옮기지 못했습니다.' })
    } finally {
      setPendingTaskId(null)
      setDragState(null)
      setDropTarget(null)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    const task = window.setTimeout(() => {
      void loadTasks(controller.signal).catch(() => {
        if (!controller.signal.aborted) setStatus('error')
      })
    }, 0)
    return () => {
      window.clearTimeout(task)
      controller.abort()
    }
  // Initial matrix load only; pagination uses the latest section state through the observer effect.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 상태 필터가 바뀌면 커서가 무의미해지므로 모든 구간을 처음부터 다시 받는다.
  const seenStatusFilter = useRef(statusFilter)
  useEffect(() => {
    if (seenStatusFilter.current === statusFilter) return
    seenStatusFilter.current = statusFilter
    void loadTasks()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  // 다른 화면에서 할 일이 만들어지면 섹션 페이지를 다시 받는다. 커서를 모르면 끼워 넣을 수 없다.
  const seenTaskListRevision = useRef(taskListRevision)
  useEffect(() => {
    if (seenTaskListRevision.current === taskListRevision) return
    seenTaskListRevision.current = taskListRevision
    void loadTasks()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskListRevision])

  const groupedTasks = useMemo(() => matrixSections.map((section) => ({
    ...section,
    // 목록 순서는 서버가 정하고, 값은 taskStore가 최신이다.
    tasks: (sections[section.id]?.items ?? []).map((task) => ({ ...task, ...tasksById[task.id] })),
    sectionState: sections[section.id] ?? initialSectionState(),
  })), [sections, tasksById])

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
            <section
              className={styles.quadrant}
              aria-labelledby={`${section.id}-title`}
              key={section.id}
              onScroll={(event) => {
                const element = event.currentTarget
                const isNearBottom = element.scrollTop + element.clientHeight >= element.scrollHeight - 48
                if (isNearBottom) void loadSection(section)
              }}
              onDragOver={(event) => {
                if (!dragState) return
                event.preventDefault()
                event.dataTransfer.dropEffect = 'move'
                autoScrollDuringDrag(event.currentTarget, event.clientY)
                setDropTarget((current) => current?.sectionId === section.id && current.taskId === null
                  ? current : { sectionId: section.id, taskId: null })
              }}
              onDrop={(event) => {
                event.preventDefault()
                void dropTask(section, null, false)
              }}
            >
              <header className={styles['quadrant-header']}>
                <h3 id={`${section.id}-title`}>{section.title}</h3>
                <div className={styles['quadrant-actions']}>
                  <AddItemButton
                    type="button"
                    aria-label={`${section.title} 영역에 할 일 추가`}
                    onClick={() => setAddDraft({
                      priority: section.id === 'priority' || section.id === 'priority-urgent',
                      urgent: section.id === 'urgent' || section.id === 'priority-urgent',
                    })}
                  />
                </div>
              </header>
              {section.tasks.length === 0 && section.sectionState.status === 'loading' ? (
                <div className={styles.sectionLoading} role="status">
                  <IconLoader2 className={styles.spinner} size={16} aria-hidden="true" />
                  불러오는 중…
                </div>
              ) : section.tasks.length === 0 && section.sectionState.status === 'error' ? (
                <div className={styles.empty}>
                  <p>이 영역을 불러오지 못했습니다.</p>
                  <button type="button" className={styles.loadMore} onClick={() => void loadSection(section)}>다시 시도</button>
                </div>
              ) : section.tasks.length === 0 ? (
                <p className={styles.empty}>할 일을 추가해주세요.</p>
              ) : (
                <ol className={styles.list}>
                  {section.tasks.map((task) => {
                    const isPending = pendingTaskId === task.id
                    return (
                      <li
                        className={`${styles[`is-${task.status.toLowerCase()}`]} ${dropTarget?.sectionId === section.id && dropTarget.taskId === task.id ? styles.dropTarget : ''} ${dragState?.taskId === task.id ? styles.dragging : ''}`}
                        aria-busy={isPending}
                        key={task.id}
                        draggable={!isPending}
                        onDragStart={(event) => {
                          event.dataTransfer.effectAllowed = 'move'
                          event.dataTransfer.setData('text/plain', String(task.id))
                          event.dataTransfer.setDragImage(event.currentTarget, event.currentTarget.offsetWidth / 2, event.currentTarget.offsetHeight / 2)
                          setDragState({ taskId: task.id, sourceSection: section.id })
                        }}
                        onDragEnd={() => {
                          setDragState(null)
                          setDropTarget(null)
                        }}
                        onDragOver={(event) => {
                          if (!dragState || dragState.taskId === task.id) return
                          event.preventDefault()
                          event.stopPropagation()
                          event.dataTransfer.dropEffect = 'move'
                          const container = event.currentTarget.closest(`.${styles.quadrant}`)
                          if (container instanceof HTMLElement) autoScrollDuringDrag(container, event.clientY)
                          event.currentTarget.dataset.dropPosition = event.clientY < event.currentTarget.getBoundingClientRect().top + event.currentTarget.offsetHeight / 2 ? 'before' : 'after'
                          setDropTarget({ sectionId: section.id, taskId: task.id })
                        }}
                        onDrop={(event) => {
                          event.preventDefault()
                          event.stopPropagation()
                          const insertBefore = event.currentTarget.dataset.dropPosition !== 'after'
                          delete event.currentTarget.dataset.dropPosition
                          void dropTask(section, task.id, insertBefore)
                        }}
                      >
                        <ChecklistCard
                          status={task.status}
                          ariaLabel={`${task.title} ${task.status === 'DONE' ? '완료 취소' : '완료 처리'}`}
                          disabled={isPending}
                          onToggle={() => void changeTaskStatus(task, task.status === 'DONE' ? 'TODO' : 'DONE')}
                          title={(
                            <div className={styles.content}>
                              <h4>
                                <InlineEditableText
                                  className={styles['task-title']}
                                  wrap
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
                          description={task.folderId === null ? undefined : (
                            <FolderLink
                              folderId={task.folderId}
                              name={folderNameById.get(task.folderId)}
                            />
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
                                <TaskFlagMenuItems
                                  disabled={isPending}
                                  session={{ onStart: () => void startSession(task), isPending }}
                                  onMove={() => setMoveTarget(task)}
                                  onDelete={() => void deleteTask(task)}
                                />
                              </TaskMenu>
                            </>
                          )}
                        />
                      </li>
                    )
                  })}
                </ol>
              )}
              {section.sectionState.status === 'error' && section.tasks.length > 0 && (
                <button type="button" className={styles.loadMore} onClick={() => void loadSection(section)}>
                  더 불러오기
                </button>
              )}
              {section.sectionState.status === 'loading' && section.tasks.length > 0 && (
                <div className={styles.sectionLoading} role="status" aria-label="추가 목록을 불러오는 중">
                  <IconLoader2 className={styles.spinner} size={16} aria-hidden="true" />
                </div>
              )}
            </section>
          ))}
        </div>
      )}
      {moveTarget && (
        <TaskInfoModal
          taskId={moveTarget.id}
          taskTitle={moveTarget.title}
          currentFolderId={moveTarget.folderId}
          currentPriority={moveTarget.priority}
          currentUrgent={moveTarget.urgent}
          canEditFlags={false}
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
      {addDraft && (
        <TaskPickerModal
          selectedTaskIds={new Set()}
          initialPriority={addDraft.priority}
          initialUrgent={addDraft.urgent}
          onAdd={async () => undefined}
          onAddTask={async (title, folderId, priority, urgent, planDate) => {
            await createTaskWithOptionalPlan({
              title,
              priority,
              urgent,
              folderId,
              planDate,
            })
            // 생성 응답에 캘린더 항목이 없어 로컬 패치가 안 된다. 그 달을 다시 받게 한다.
            if (planDate) invalidatePlanDate(planDate)
            await loadTasks()
          }}
          onClose={() => setAddDraft(null)}
        />
      )}
    </section>
  )
}
