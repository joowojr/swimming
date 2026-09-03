import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {
    IconChevronLeft,
    IconChevronRight,
    IconLoader2,
    IconPlus,
} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import InlineEditableText from '../../components/InlineEditableText'
import ChecklistCard from '../../components/ChecklistCard'
import TaskMenu, { TaskFlagMenuItems } from '../../components/TaskMenu'
import {useNavigate} from 'react-router-dom'
import type {FolderDetail} from '../folders/folderTypes.ts'
import CreateSessionModal from '../sessions/CreateSessionModal'
import {updateTaskPriority, updateTaskStatus, updateTaskTitle, updateTaskUrgent} from '../tasks/taskApi'
import {TASK_STATUS_LABEL, TASK_STATUS_VALUES} from '../tasks/taskLabels'
import {addDailyPlanItems, deleteDailyPlanItem, getDailyPlans} from './dailyPlanApi'
import type {TaskResponse, TaskStatus} from '../tasks/taskTypes'
import type {DailyPlan, DailyPlanItem} from './dailyPlanTypes'
import TaskPickerModal from './TaskPickerModal'
import styles from './DailyPlanner.module.css'

type TaskOverride = Partial<Pick<DailyPlanItem, 'title' | 'status' | 'priority' | 'urgent'>>

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {year: 'numeric', month: 'long'})
const selectedDateFormatter = new Intl.DateTimeFormat('ko-KR', {month: 'long', day: 'numeric', weekday: 'long'})
const dayLabels = ['일', '월', '화', '수', '목', '금', '토']

function formatDate(date: Date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

function parseDate(value: string) {
    return new Date(`${value}T00:00:00`)
}

function monthRange(month: Date) {
    const first = new Date(month.getFullYear(), month.getMonth(), 1)
    const last = new Date(month.getFullYear(), month.getMonth() + 1, 0)
    return {fromDate: formatDate(first), toDate: formatDate(last)}
}

function startOfMonth(date: Date) {
    return new Date(date.getFullYear(), date.getMonth(), 1)
}

function monthDays(month: Date) {
    const first = new Date(month.getFullYear(), month.getMonth(), 1)
    const count = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
    const leading = first.getDay()
    return Array.from({length: Math.ceil((leading + count) / 7) * 7}, (_, index) => {
        const day = index - leading + 1
        return day < 1 || day > count ? null : new Date(month.getFullYear(), month.getMonth(), day)
    })
}

export default function DailyPlanner() {
    const navigate = useNavigate()
    const today = useMemo(() => formatDate(new Date()), [])
    const [selectedDate, setSelectedDate] = useState(today)
    const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(parseDate(today)))
    const [drafts, setDrafts] = useState<Record<string, DailyPlanItem[]>>({})
    const [taskOverrides, setTaskOverrides] = useState<Record<number, TaskOverride>>({})
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [message, setMessage] = useState<string | null>(null)
    const [isPickerOpen, setIsPickerOpen] = useState(false)
    const [sessionTaskId, setSessionTaskId] = useState<number | null>(null)
    const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)

    // 이미 받아둔 달. 계획이 바뀐 달은 여기서 빼서 다시 열 때 새로 받는다.
    const loadedMonthsRef = useRef<Set<string>>(new Set())
    // 늦게 도착한 이전 달 응답이 현재 달을 덮어쓰지 않게 한다.
    const loadRequestRef = useRef(0)

    const {fromDate, toDate} = useMemo(() => monthRange(visibleMonth), [visibleMonth])
    const visibleMonthKey = fromDate.slice(0, 7)

    // quiet: 이미 화면에 목록이 있는 상태의 갱신이라 로딩 표시를 띄우지 않는다.
    const loadMonth = useCallback(async (monthKey: string, from: string, to: string, quiet = false) => {
        const requestId = ++loadRequestRef.current
        if (!quiet) setStatus('loading')
        try {
            const response = await getDailyPlans(from, to)
            if (requestId !== loadRequestRef.current) return
            setDrafts((current) => ({
                ...current,
                ...Object.fromEntries(response.map((plan) => [plan.date, plan.items])),
            }))
            loadedMonthsRef.current.add(monthKey)
            setStatus('ready')
        } catch {
            if (requestId !== loadRequestRef.current) return
            if (quiet) setMessage('계획을 최신 상태로 가져오지 못했습니다.')
            else setStatus('error')
        }
    }, [])

    // 계획이 바뀐 달은 캐시를 버리고 곧바로 새로 받는다.
    const refreshMonth = useCallback(async (date: string) => {
        const monthKey = date.slice(0, 7)
        const range = monthRange(parseDate(date))
        loadedMonthsRef.current.delete(monthKey)
        await loadMonth(monthKey, range.fromDate, range.toDate, true)
    }, [loadMonth])

    useEffect(() => {
        if (loadedMonthsRef.current.has(visibleMonthKey)) {
            setStatus('ready')
            return
        }
        void loadMonth(visibleMonthKey, fromDate, toDate)
    }, [visibleMonthKey, fromDate, toDate, loadMonth])

    const applyTaskOverride = (item: DailyPlanItem): DailyPlanItem => {
        const override = taskOverrides[item.taskId]
        if (!override) return item
        return {...item, ...override}
    }
    const items = (drafts[selectedDate] ?? []).map(applyTaskOverride)
    const todayTasks = (drafts[today] ?? []).map(applyTaskOverride)
    const calendarDays = useMemo(() => monthDays(visibleMonth), [visibleMonth])

    const selectDate = (date: string) => {
        setSelectedDate(date)
        // 같은 달 안에서 날짜만 옮기면 visibleMonth를 그대로 두어 재조회를 막는다.
        setVisibleMonth((current) => {
            const next = startOfMonth(parseDate(date))
            return next.getTime() === current.getTime() ? current : next
        })
        setMessage(null)
    }

    const moveMonth = (amount: number) => {
        const nextMonth = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + amount, 1)
        const {fromDate} = monthRange(nextMonth)
        setVisibleMonth(nextMonth)
        setSelectedDate(fromDate)
        setMessage(null)
    }

    const replacePlan = (savedPlan: DailyPlan) => {
        setDrafts((current) => ({...current, [savedPlan.date]: savedPlan.items}))
        void refreshMonth(savedPlan.date)
    }

    const addTasks = async (tasks: FolderDetail['tasks']) => {
        if (tasks.length === 0) return
        replacePlan(await addDailyPlanItems(selectedDate, {taskIds: tasks.map((task) => task.id)}))
    }

    const addTask = async (title: string, folderId: number | null, priority: boolean, urgent: boolean) => {
        replacePlan(await addDailyPlanItems(selectedDate, {
            title,
            priority,
            urgent,
            ...(folderId === null ? {} : {folderId}),
        }))
    }

    const updateTaskOverride = (
        taskId: number,
        field: 'title' | 'status' | 'priority' | 'urgent',
        value: string | boolean,
    ) => {
        setTaskOverrides((current) => ({
            ...current,
            [taskId]: {
                ...current[taskId],
                [field]: value,
            },
        }))
    }

    const updateTaskFlags = (task: TaskResponse) => {
        updateTaskOverride(task.id, 'priority', task.priority)
        updateTaskOverride(task.id, 'urgent', task.urgent)
    }

    const changeTaskTitle = async (item: DailyPlanItem, title: string) => {
        await updateTaskTitle(item.taskId, {title})
        updateTaskOverride(item.taskId, 'title', title)
    }

    const changeTaskStatus = async (item: DailyPlanItem, nextStatus: TaskStatus) => {
        setPendingTaskId(item.taskId)
        setMessage(null)
        try {
            await updateTaskStatus(item.taskId, {status: nextStatus})
            updateTaskOverride(item.taskId, 'status', nextStatus)
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '상태를 변경하지 못했습니다. 다시 시도해 주세요.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const changeTaskPriority = async (item: DailyPlanItem) => {
        setPendingTaskId(item.taskId)
        setMessage(null)
        try {
            updateTaskFlags(await updateTaskPriority(item.taskId, {priority: !item.priority}))
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '중요 표시를 변경하지 못했습니다.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const changeTaskUrgent = async (item: DailyPlanItem) => {
        setPendingTaskId(item.taskId)
        setMessage(null)
        try {
            updateTaskFlags(await updateTaskUrgent(item.taskId, {urgent: !item.urgent}))
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '즉시 표시를 변경하지 못했습니다.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const removeItem = async (itemId: number) => {
        try {
            await deleteDailyPlanItem(selectedDate, itemId)
            setDrafts((current) => ({
                ...current,
                [selectedDate]: (current[selectedDate] ?? []).filter((item) => item.id !== itemId),
            }))
            void refreshMonth(selectedDate)
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '계획에서 할 일을 제거하지 못했습니다.')
        }
    }

    const getTaskTitleError = (error: unknown) => {
        const apiError = typeof error === 'object' && error !== null ? error as ApiError : undefined
        return apiError?.errors?.title ?? apiError?.message ?? '할 일 제목을 저장하지 못했습니다.'
    }

    return (
        <section className={styles.widget} aria-labelledby="daily-planner-title">
            <div className={styles.calendarPanel}>
                <header className={styles.calendarHeader}>
                    <h2>{dateFormatter.format(visibleMonth)}</h2>
                    <div className={styles.calendarNav}>
                        <button type="button" aria-label="이전 달" onClick={() => moveMonth(-1)}>
                            <IconChevronLeft size={18} aria-hidden="true" />
                        </button>
                        <button type="button" aria-label="다음 달" onClick={() => moveMonth(1)}>
                            <IconChevronRight size={18} aria-hidden="true" />
                        </button>
                    </div>
                </header>
                <div className={styles.weekdays} aria-hidden="true">
                    {dayLabels.map((label) => <span key={label}>{label}</span>)}
                </div>
                <div className={styles.calendarGrid} role="grid" aria-label="계획 날짜 선택">
                    {calendarDays.map((date, index) => {
                        if (!date) return <span className={styles.outsideDay} key={`empty-${index}`} aria-hidden="true" />
                        const dateValue = formatDate(date)
                        const selected = dateValue === selectedDate
                        const isToday = dateValue === today
                        const hasItems = (drafts[dateValue] ?? []).length > 0
                        return (
                            <button
                                type="button"
                                role="gridcell"
                                className={styles.day}
                                data-selected={selected}
                                data-today={isToday}
                                aria-selected={selected}
                                aria-label={`${dateValue}${hasItems ? `, 할 일 ${drafts[dateValue].length}개` : ''}`}
                                onClick={() => selectDate(dateValue)}
                                key={dateValue}
                            >
                                {date.getDate()}
                                {hasItems && <span className={styles.dayMark} aria-hidden="true" />}
                            </button>
                        )
                    })}
                </div>
                <ModalTriggerButton className={`${styles.addTask} ${styles.calendarAddTask}`} dialogId="task-picker-dialog" variant="plain" icon={<IconPlus size={17} aria-hidden="true" />} onClick={() => setIsPickerOpen(true)}>
                    할 일 추가
                </ModalTriggerButton>
                <p className={styles.calendarHint}>날짜를 선택하면 해당 날짜의 계획을 확인할 수 있습니다.</p>
            </div>

            <div className={styles.todoPanel}>
                <header className={styles.todoHeader}>
                    <div>
                        <p className={styles.eyebrow}>오늘의 계획</p>
                        <h1 id="daily-planner-title">{selectedDateFormatter.format(parseDate(selectedDate))}</h1>
                    </div>
                    <span className={styles.taskCount}>{items.length}개</span>
                </header>
                {message && <p className={styles.message} role="alert">{message}</p>}

                {status === 'loading' ? (
                    <div className={styles.state} role="status"><IconLoader2 className={styles.spinner} size={19} />계획을 불러오는 중…</div>
                ) : status === 'error' ? (
                    <div className={styles.state}><p>계획을 불러오지 못했습니다.</p><button type="button" onClick={() => void loadMonth(visibleMonthKey, fromDate, toDate)}>다시 불러오기</button></div>
                ) : (
                    <>
                        <ol className={styles.todoList}>
                            {items.map((item) => (
                                <li key={item.id} className={styles[`is-${item.status.toLowerCase()}`]}>
                                    <ChecklistCard
                                        status={item.status}
                                        title={
                                            <InlineEditableText
                                                value={item.title}
                                                ariaLabel={`${item.urgent ? '즉시 ' : ''}${item.priority ? '중요 ' : ''}Task 제목`}
                                                maxLength={255}
                                                className={[
                                                    styles['task-title-editor'],
                                                    item.priority && styles['is-priority'],
                                                    item.urgent && styles['is-urgent'],
                                                ].filter(Boolean).join(' ')}
                                                requiredMessage="Task 제목을 입력해 주세요."
                                                onSave={(title) => changeTaskTitle(item, title)}
                                                getErrorMessage={getTaskTitleError}
                                            />
                                        }
                                        description={item.itemType === 'TASK' ? item.folderName : undefined}
                                        ariaLabel={`${item.title} ${item.status === 'DONE' ? '완료 취소' : '완료 처리'}`}
                                        disabled={pendingTaskId === item.taskId}
                                        onToggle={() => void changeTaskStatus(item, item.status === 'DONE' ? 'TODO' : 'DONE')}
                                        actions={(
                                          <>
                                        <select
                                            className={styles.status}
                                            data-status={item.status}
                                            value={item.status}
                                            aria-label={`${item.title} 상태`}
                                            disabled={pendingTaskId === item.taskId}
                                            onChange={(event) => void changeTaskStatus(item, event.target.value as TaskStatus)}
                                        >
                                            {TASK_STATUS_VALUES.map((taskStatus) => <option value={taskStatus} key={taskStatus}>{TASK_STATUS_LABEL[taskStatus]}</option>)}
                                        </select>
                                        <TaskMenu inline label={`${item.title} 카드 메뉴`}>
                                            <TaskFlagMenuItems
                                                priority={item.priority}
                                                urgent={item.urgent}
                                                disabled={pendingTaskId === item.taskId}
                                                onTogglePriority={() => void changeTaskPriority(item)}
                                                onToggleUrgent={() => void changeTaskUrgent(item)}
                                                session={selectedDate === today ? {
                                                    onStart: () => setSessionTaskId(item.taskId),
                                                    dialogId: 'create-session-dialog',
                                                    isOpen: sessionTaskId === item.taskId,
                                                } : undefined}
                                                onDelete={() => void removeItem(item.id)}
                                            />
                                        </TaskMenu>
                                          </>
                                        )}
                                    />
                                </li>
                            ))}
                        </ol>
                        {items.length === 0 && <p className={styles.empty}>이 날짜에는 계획된 할 일이 없습니다.</p>}
                    </>
                )}
            </div>

            {isPickerOpen && <TaskPickerModal selectedTaskIds={new Set(items.map((item) => item.taskId))} initialPlanDate={selectedDate} onAdd={addTasks} onAddTask={addTask} onClose={() => setIsPickerOpen(false)} />}
            {sessionTaskId !== null && <CreateSessionModal todayTasks={todayTasks} initialTaskId={sessionTaskId} onClose={() => setSessionTaskId(null)} onStarted={(session) => { setSessionTaskId(null); navigate(`/sessions/${session.id}`) }} />}
        </section>
    )
}
