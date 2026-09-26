import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ComponentProps, ReactNode } from 'react'
import {
  DndContext,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  closestCenter,
  getFirstCollision,
  pointerWithin,
  rectIntersection,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import type { CollisionDetection, DragEndEvent, DragOverEvent, UniqueIdentifier } from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { IconLoader2 } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import AddItemButton from '../../components/AddItemButton'
import LoadMoreButton from '../../components/LoadMoreButton'
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
import { createTasksBatch } from './taskApi'
import { deleteTasks, getTaskMatrixPage, moveTask, updateTaskInfo, updateTaskStatus, updateTaskTitle } from './taskApi'
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
    title: '일정' +
        '',
    apiSection: 'STANDARD',
  },
]

const initialSectionState = (): SectionState => ({ items: [], nextCursor: null, hasNext: true, status: 'loading' })

/** 입력 중인 칸에서 글자를 고르거나 값을 고르는 동작은 드래그로 가로채지 않는다. 버튼은 짧게 누르면 그대로 눌린다. */
function startsOnFormField(target: EventTarget | null) {
  return target instanceof Element
    && target.closest('input, select, textarea, [contenteditable="true"]') !== null
}

/** 카드 전체를 잡아 끈다. 마우스는 조금 움직여야, 터치는 길게 눌러야 시작해 클릭·스크롤과 구분된다. */
class CardMouseSensor extends MouseSensor {
  static activators = MouseSensor.activators.map(({ eventName, handler }) => ({
    eventName,
    handler: (...args: Parameters<typeof handler>) =>
      !startsOnFormField(args[0].nativeEvent.target) && handler(...args),
  }))
}

class CardTouchSensor extends TouchSensor {
  static activators = TouchSensor.activators.map(({ eventName, handler }) => ({
    eventName,
    handler: (...args: Parameters<typeof handler>) =>
      !startsOnFormField(args[0].nativeEvent.target) && handler(...args),
  }))
}

/** 구간 드롭 영역 id. 숫자인 task id와 겹치지 않게 접두사를 붙인다. */
const SECTION_DROP_PREFIX = 'section:'

/** 드롭 대상이 구간 자체인지 task인지에 따라 그 task가 들어 있는 구간 id를 찾는다. */
function findSectionId(sections: Record<string, SectionState>, id: UniqueIdentifier): string | undefined {
  if (typeof id === 'string' && id.startsWith(SECTION_DROP_PREFIX)) return id.slice(SECTION_DROP_PREFIX.length)
  return Object.entries(sections).find(([, section]) => section.items.some((task) => task.id === id))?.[0]
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
  /** 끌기 시작한 순간의 구간들. 제자리 판단과 취소·실패 시 복구에 쓴다. */
  const sectionsBeforeDragRef = useRef<Record<string, SectionState> | null>(null)
  const lastOverIdRef = useRef<UniqueIdentifier | null>(null)
  /** 구간을 막 옮긴 직후에는 레이아웃이 다시 잡히기 전이라 충돌 판정이 원래 구간으로 튈 수 있다. */
  const recentlyMovedToNewSectionRef = useRef(false)

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
  const applyTaskDate = useDailyPlanStore((state) => state.applyTaskDate)

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

  const sensors = useSensors(
    useSensor(CardMouseSensor, { activationConstraint: { distance: 4 } }),
    useSensor(CardTouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      recentlyMovedToNewSectionRef.current = false
    })
    return () => cancelAnimationFrame(frame)
  }, [sections])

  /**
   * dnd-kit 다중 목록 예제의 충돌 판정이다. 구간을 넘나들 때 판정이 두 구간 사이를 오가며
   * 상태를 계속 바꾸는(엉키는) 문제를 막는다. 포인터가 구간 위에 있으면 그 구간에서 가장 가까운 카드를 고른다.
   */
  const collisionDetection = useCallback<CollisionDetection>((args) => {
    const pointerCollisions = pointerWithin(args)
    const collisions = pointerCollisions.length > 0 ? pointerCollisions : rectIntersection(args)
    let overId = getFirstCollision(collisions, 'id')

    if (overId != null) {
      if (typeof overId === 'string' && overId.startsWith(SECTION_DROP_PREFIX)) {
        const itemIds = new Set<UniqueIdentifier>(
          (sections[overId.slice(SECTION_DROP_PREFIX.length)]?.items ?? []).map((task) => task.id),
        )
        if (itemIds.size > 0) {
          overId = closestCenter({
            ...args,
            droppableContainers: args.droppableContainers.filter((container) => itemIds.has(container.id)),
          })[0]?.id ?? overId
        }
      }
      lastOverIdRef.current = overId
      return [{ id: overId }]
    }

    if (recentlyMovedToNewSectionRef.current) lastOverIdRef.current = args.active.id
    return lastOverIdRef.current != null ? [{ id: lastOverIdRef.current }] : []
  }, [sections])

  const handleDragStart = () => {
    sectionsBeforeDragRef.current = sectionsRef.current
    lastOverIdRef.current = null
    setUpdateError(null)
  }

  /** 다른 구간 위로 넘어가는 순간 화면에서 먼저 옮겨 놓을 자리를 보여 준다. 저장은 놓을 때 한다. */
  const handleDragOver = ({ active, over }: DragOverEvent) => {
    if (!over) return
    setSections((current) => {
      const fromId = findSectionId(current, active.id)
      const toId = findSectionId(current, over.id)
      if (!fromId || !toId || fromId === toId) return current
      const task = current[fromId].items.find((item) => item.id === active.id)
      if (!task) return current
      const targetItems = current[toId].items
      const overIndex = targetItems.findIndex((item) => item.id === over.id)
      // 끄는 카드가 대상 카드보다 아래로 내려갔으면 그 뒤에 넣는다.
      const translated = active.rect.current.translated
      const isBelowOver = translated !== null && translated.top > over.rect.top + over.rect.height / 2
      const insertAt = overIndex >= 0 ? overIndex + (isBelowOver ? 1 : 0) : targetItems.length
      recentlyMovedToNewSectionRef.current = true
      return {
        ...current,
        [fromId]: { ...current[fromId], items: current[fromId].items.filter((item) => item.id !== active.id) },
        [toId]: { ...current[toId], items: [...targetItems.slice(0, insertAt), task, ...targetItems.slice(insertAt)] },
      }
    })
  }

  const handleDragCancel = () => {
    if (sectionsBeforeDragRef.current) setSections(sectionsBeforeDragRef.current)
    sectionsBeforeDragRef.current = null
  }

  const handleDragEnd = async ({ active, over }: DragEndEvent) => {
    const before = sectionsBeforeDragRef.current
    sectionsBeforeDragRef.current = null
    const taskId = Number(active.id)
    const current = sectionsRef.current
    const targetSectionId = over ? findSectionId(current, over.id) : undefined
    const sourceSectionId = before ? findSectionId(before, taskId) : undefined
    if (!before || !over || !targetSectionId || !sourceSectionId) {
      if (before) setSections(before)
      return
    }

    // 같은 구간 안에서는 놓은 자리로 순서를 바꾼다. 다른 구간으로는 onDragOver가 이미 옮겼다.
    let targetItems = current[targetSectionId].items
    const fromIndex = targetItems.findIndex((task) => task.id === taskId)
    const overIndex = targetItems.findIndex((task) => task.id === over.id)
    if (fromIndex >= 0 && overIndex >= 0 && fromIndex !== overIndex) {
      targetItems = arrayMove(targetItems, fromIndex, overIndex)
    }
    const targetIndex = targetItems.findIndex((task) => task.id === taskId)
    const sourceIndex = before[sourceSectionId].items.findIndex((task) => task.id === taskId)
    if (sourceSectionId === targetSectionId && targetIndex === sourceIndex) {
      setSections(before)
      return
    }

    setSections({ ...current, [targetSectionId]: { ...current[targetSectionId], items: targetItems } })
    const targetSection = matrixSections.find((section) => section.id === targetSectionId)
    const sourceSection = matrixSections.find((section) => section.id === sourceSectionId)
    if (!targetSection) return
    setPendingTaskId(taskId)
    try {
      await moveTask(taskId, {
        scope: 'MATRIX',
        targetSection: targetSection.apiSection,
        previousTaskId: targetItems[targetIndex - 1]?.id ?? null,
        nextTaskId: targetItems[targetIndex + 1]?.id ?? null,
        ...(statusFilter === 'ALL' ? {} : { status: statusFilter }),
      })
      await Promise.all([
        sourceSection && sourceSection !== targetSection ? loadSection(sourceSection, true) : Promise.resolve(),
        loadSection(targetSection, true),
      ])
    } catch (error: unknown) {
      setSections(before)
      const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
      setUpdateError({ taskId, message: apiMessage ?? '할 일을 옮기지 못했습니다.' })
    } finally {
      setPendingTaskId(null)
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
        <DndContext
          sensors={sensors}
          collisionDetection={collisionDetection}
          accessibility={{
            screenReaderInstructions: {
              draggable: '카드에 초점을 둔 채 스페이스바를 누르고 방향키로 움직인 뒤 스페이스바로 놓으세요. 취소는 Esc예요.',
            },
          }}
          onDragStart={handleDragStart}
          onDragOver={handleDragOver}
          onDragEnd={(event) => void handleDragEnd(event)}
          onDragCancel={handleDragCancel}
        >
        <div className={styles.grid}>
          {groupedTasks.map((section) => (
            <DroppableQuadrant
              sectionId={section.id}
              className={styles.quadrant}
              aria-labelledby={`${section.id}-title`}
              key={section.id}
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
                <SortableContext items={section.tasks.map((task) => task.id)} strategy={verticalListSortingStrategy}>
                <ol className={styles.list}>
                  {section.tasks.map((task) => {
                    const isPending = pendingTaskId === task.id
                    return (
                      <SortableTask
                        className={styles[`is-${task.status.toLowerCase()}`]}
                        taskId={task.id}
                        disabled={isPending}
                        isPending={isPending}
                        key={task.id}
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
                      </SortableTask>
                    )
                  })}
                </ol>
                </SortableContext>
              )}
              {section.tasks.length > 0 && section.sectionState.status === 'error' && (
                <p className={styles.error} role="alert">더 불러오지 못했습니다. 다시 눌러 주세요.</p>
              )}
              {section.tasks.length > 0 && section.sectionState.hasNext && (
                <LoadMoreButton
                  className={styles['load-more']}
                  isLoading={section.sectionState.status === 'loading'}
                  onClick={() => void loadSection(section)}
                />
              )}
            </DroppableQuadrant>
          ))}
        </div>
        </DndContext>
      )}
      {moveTarget && (
        <TaskInfoModal
          taskId={moveTarget.id}
          taskTitle={moveTarget.title}
          currentFolderId={moveTarget.folderId}
          currentPriority={moveTarget.priority}
          currentUrgent={moveTarget.urgent}
          canEditFlags={false}
          currentPlanDate={moveTarget.planDate}
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
          canMoveFromOtherDates
          applyAttributesToExisting
          initialPriority={addDraft.priority}
          initialUrgent={addDraft.urgent}
          onAddTasks={async ({ existingTasks, newTasks, planDate }) => {
            // 이미 있는 할 일은 담을 날짜와 이 영역의 즉시·중요로 바꾼다. 모달은 날짜 없이 이미 있는 할 일을 제출하지 않는다.
            // folderId를 빼서 보내 폴더는 건드리지 않는다(삭제된 폴더에 걸린 할 일도 연결을 유지한다).
            if (existingTasks.length > 0 && planDate) {
              const updated = await Promise.all(existingTasks.map(({ taskId, title, priority, urgent }) => (
                updateTaskInfo(taskId, { title, priority, urgent, planDate })
              )))
              upsertTasks(updated)
              updated.forEach((task) => applyTaskDate(task.id, task.planDate))
            }
            if (newTasks.length > 0) {
              await createTasksBatch({
                tasks: newTasks.map((task) => ({ ...task, planDate })),
              })
              // 생성 응답에 캘린더 항목이 없어 로컬 패치가 안 된다. 그 달을 다시 받게 한다.
              if (planDate) invalidatePlanDate(planDate)
            }
            await loadTasks()
          }}
          onClose={() => setAddDraft(null)}
        />
      )}
    </section>
  )
}

type DroppableQuadrantProps = ComponentProps<'section'> & { sectionId: string }

/** 구간 전체가 드롭 영역이다. 비어 있는 구간에도 할 일을 놓을 수 있다. */
function DroppableQuadrant({ sectionId, className, ...props }: DroppableQuadrantProps) {
  const { setNodeRef, isOver } = useDroppable({ id: `${SECTION_DROP_PREFIX}${sectionId}` })
  return (
    <section
      ref={setNodeRef}
      className={`${className ?? ''} ${isOver ? styles['quadrant-over'] : ''}`}
      {...props}
    />
  )
}

interface SortableTaskProps {
  taskId: number
  className?: string
  disabled: boolean
  isPending: boolean
  children: ReactNode
}

function SortableTask({ taskId, className, disabled, isPending, children }: SortableTaskProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: taskId,
    disabled,
    // 카드 안에 버튼이 여럿 있어 카드 자체를 버튼으로 알리지 않는다.
    attributes: { role: 'listitem', roleDescription: '옮길 수 있는 할 일' },
  })

  return (
    <li
      ref={(element) => {
        setNodeRef(element)
        // 키보드 드래그는 카드 자체에 초점이 있을 때만 시작한다. 안쪽 버튼의 스페이스바는 그대로 둔다.
        setActivatorNodeRef(element)
      }}
      className={`${className ?? ''} ${isDragging ? styles.dragging : ''}`}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      aria-busy={isPending}
      {...attributes}
      {...listeners}
    >
      {children}
    </li>
  )
}
