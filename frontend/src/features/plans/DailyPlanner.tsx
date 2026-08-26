import {useEffect, useMemo, useState} from 'react'
import {
    IconChevronLeft,
    IconChevronRight,
    IconLoader2,
    IconPlayerPlay,
    IconPlus,
} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import InlineEditableText from '../../components/InlineEditableText'
import DeleteIconButton from '../../components/DeleteIconButton'
import ChecklistCard from '../../components/ChecklistCard'
import {useNavigate} from 'react-router-dom'
import type {Project, ProjectDetail} from '../projects/projectTypes'
import CreateSessionModal from '../sessions/CreateSessionModal'
import {updateTask} from '../tasks/taskApi'
import {TASK_STATUS_LABEL, TASK_STATUS_VALUES} from '../tasks/taskLabels'
import {addDailyPlanItems, deleteDailyPlanItem, getDailyPlans} from './dailyPlanApi'
import type {TaskStatus} from '../tasks/taskTypes'
import type {DailyPlan, DailyPlanItem} from './dailyPlanTypes'
import DailyPlanCardMenu from './DailyPlanCardMenu'
import TaskPickerModal from './TaskPickerModal'
import styles from './DailyPlanner.module.css'

interface DailyPlannerProps {
    projects: Project[]
}

type TaskOverride = Pick<DailyPlanItem, 'title' | 'status'>

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

function addDays(value: string, amount: number) {
    const date = parseDate(value)
    date.setDate(date.getDate() + amount)
    return formatDate(date)
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

export default function DailyPlanner({projects}: DailyPlannerProps) {
    const navigate = useNavigate()
    const today = useMemo(() => formatDate(new Date()), [])
    const [selectedDate, setSelectedDate] = useState(today)
    const [visibleMonth, setVisibleMonth] = useState(() => parseDate(today))
    const [plans, setPlans] = useState<DailyPlan[]>([])
    const [drafts, setDrafts] = useState<Record<string, DailyPlanItem[]>>({})
    const [taskOverrides, setTaskOverrides] = useState<Record<number, TaskOverride>>({})
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [message, setMessage] = useState<string | null>(null)
    const [isPickerOpen, setIsPickerOpen] = useState(false)
    const [sessionTaskId, setSessionTaskId] = useState<number | null>(null)
    const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)

    const loadPlans = async (date: string) => {
        setStatus('loading')
        try {
            const response = await getDailyPlans(date, addDays(date, 6))
            setPlans(response)
            setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
            setTaskOverrides({})
            setStatus('ready')
        } catch {
            setStatus('error')
        }
    }

    useEffect(() => {
        let active = true
        void getDailyPlans(selectedDate, addDays(selectedDate, 6))
            .then((response) => {
                if (!active) return
                setPlans(response)
                setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
                setTaskOverrides({})
                setStatus('ready')
            })
            .catch(() => {
                if (active) setStatus('error')
            })
        return () => { active = false }
    }, [selectedDate])

    const applyTaskOverride = (item: DailyPlanItem): DailyPlanItem => {
        const override = taskOverrides[item.taskId]
        if (!override) return item
        return {
            ...item,
            title: override.title,
            status: override.status,
        }
    }
    const items = (drafts[selectedDate] ?? []).map(applyTaskOverride)
    const todayTasks = (drafts[today] ?? plans.find((plan) => plan.date === today)?.items ?? [])
        .map(applyTaskOverride)
    const calendarDays = useMemo(() => monthDays(visibleMonth), [visibleMonth])

    const selectDate = (date: string) => {
        const nextMonth = parseDate(date)
        setSelectedDate(date)
        setVisibleMonth(nextMonth)
        setMessage(null)
    }

    const replacePlan = (savedPlan: DailyPlan) => {
        setPlans((current) => current.map((plan) => plan.date === savedPlan.date ? savedPlan : plan))
        setDrafts((current) => ({...current, [savedPlan.date]: savedPlan.items}))
    }

    const addTasks = async (tasks: ProjectDetail['tasks']) => {
        if (tasks.length === 0) return
        replacePlan(await addDailyPlanItems(selectedDate, {taskIds: tasks.map((task) => task.id)}))
    }

    const addTask = async (title: string, projectId: number | null) => {
        replacePlan(await addDailyPlanItems(selectedDate, {title, ...(projectId === null ? {} : {projectId})}))
    }

    const updateTaskOverride = (
        taskId: number,
        field: 'title' | 'status',
        value: string,
    ) => {
        setTaskOverrides((current) => ({
            ...current,
            [taskId]: {
                ...current[taskId],
                [field]: value,
            } as TaskOverride,
        }))
    }

    const changeTaskTitle = async (item: DailyPlanItem, title: string) => {
        await updateTask(item.taskId, {title, status: item.status})
        updateTaskOverride(item.taskId, 'title', title)
    }

    const changeTaskStatus = async (item: DailyPlanItem, nextStatus: TaskStatus) => {
        setPendingTaskId(item.taskId)
        setMessage(null)
        try {
            await updateTask(item.taskId, {title: item.title, status: nextStatus})
            updateTaskOverride(item.taskId, 'status', nextStatus)
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '할 일 상태를 변경하지 못했습니다. 다시 시도해 주세요.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const removeItem = async (itemId: number) => {
        try {
            await deleteDailyPlanItem(selectedDate, itemId)
            const remove = (dateItems: DailyPlanItem[]) => dateItems.filter((item) => item.id !== itemId).map((item, orderIdx) => ({...item, orderIdx}))
            setDrafts((current) => ({...current, [selectedDate]: remove(current[selectedDate] ?? [])}))
            setPlans((current) => current.map((plan) => plan.date === selectedDate ? {...plan, items: remove(plan.items)} : plan))
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
                        <button type="button" aria-label="이전 달" onClick={() => setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() - 1, 1))}>
                            <IconChevronLeft size={18} aria-hidden="true" />
                        </button>
                        <button type="button" aria-label="다음 달" onClick={() => setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + 1, 1))}>
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
                    <div className={styles.state}><p>계획을 불러오지 못했습니다.</p><button type="button" onClick={() => void loadPlans(selectedDate)}>다시 불러오기</button></div>
                ) : (
                    <>
                        <ol className={styles.todoList}>
                            {items.map((item) => (
                                <li key={item.id}>
                                    <ChecklistCard
                                        id={item.taskId}
                                        title={
                                            <InlineEditableText
                                                value={item.title}
                                                ariaLabel="Task 제목"
                                                maxLength={255}
                                                requiredMessage="Task 제목을 입력해 주세요."
                                                onSave={(title) => changeTaskTitle(item, title)}
                                                getErrorMessage={getTaskTitleError}
                                            />
                                        }
                                        description={item.projectName}
                                        checked={item.status === 'DONE'}
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
                                        <DailyPlanCardMenu inline label={`${item.title} 카드 메뉴`}>
                                            {selectedDate === today && (
                                                <ModalTriggerButton dialogId="create-session-dialog" isOpen={sessionTaskId === item.taskId} variant="plain" icon={<IconPlayerPlay size={15} aria-hidden="true" />} onClick={() => setSessionTaskId(item.taskId)}>
                                                    다이브 세션
                                                </ModalTriggerButton>
                                            )}
                                            <DeleteIconButton label="계획에서 제거" iconSize={15} onClick={() => void removeItem(item.id)} />
                                        </DailyPlanCardMenu>
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

            {isPickerOpen && <TaskPickerModal projects={projects} selectedTaskIds={new Set(items.map((item) => item.taskId))} onAdd={addTasks} onAddTask={addTask} onClose={() => setIsPickerOpen(false)} />}
            {sessionTaskId !== null && <CreateSessionModal todayTasks={todayTasks} initialTaskId={sessionTaskId} onClose={() => setSessionTaskId(null)} onStarted={(session) => { setSessionTaskId(null); navigate(`/sessions/${session.id}`) }} />}
        </section>
    )
}
