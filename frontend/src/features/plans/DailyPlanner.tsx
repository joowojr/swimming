import {useEffect, useMemo, useRef, useState} from 'react'
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
import {useNavigate} from 'react-router-dom'
import type {Project, ProjectDetail} from '../projects/projectTypes'
import CreateSessionModal from '../sessions/CreateSessionModal'
import {updateTask} from '../tasks/taskApi'
import {TASK_STATUS_LABEL, TASK_STATUS_VALUES} from '../tasks/taskLabels'
import {addDailyPlanItems, deleteDailyPlanItem, getDailyPlans, reorderDailyPlanItems} from './dailyPlanApi'
import type {TaskStatus} from '../tasks/taskTypes'
import type {DailyPlan, DailyPlanItem} from './dailyPlanTypes'
import DailyPlanCardMenu from './DailyPlanCardMenu'
import TaskPickerModal from './TaskPickerModal'
import styles from './DailyPlanner.module.css'

interface DailyPlannerProps {
    projects: Project[]
}

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
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [dirtyDates, setDirtyDates] = useState<Set<string>>(new Set())
    const [message, setMessage] = useState<string | null>(null)
    const [isPickerOpen, setIsPickerOpen] = useState(false)
    const [sessionTaskId, setSessionTaskId] = useState<number | null>(null)
    const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
    const revisionRef = useRef(new Map<string, number>())
    const savingDatesRef = useRef(new Set<string>())

    const loadPlans = async (date: string) => {
        setStatus('loading')
        try {
            const response = await getDailyPlans(date, addDays(date, 6))
            setPlans(response)
            setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
            setDirtyDates(new Set())
            revisionRef.current.clear()
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
                setDirtyDates(new Set())
                revisionRef.current.clear()
                setStatus('ready')
            })
            .catch(() => {
                if (active) setStatus('error')
            })
        return () => { active = false }
    }, [selectedDate])

    useEffect(() => {
        if (dirtyDates.size === 0) return
        const timeoutId = window.setTimeout(() => {
            dirtyDates.forEach((date) => {
                if (savingDatesRef.current.has(date)) return
                const items = drafts[date] ?? []
                const revision = revisionRef.current.get(date) ?? 0
                savingDatesRef.current.add(date)
                void reorderDailyPlanItems(date, {itemIds: items.map((item) => item.id)})
                    .then((savedPlan) => {
                        if ((revisionRef.current.get(date) ?? 0) !== revision) return
                        setPlans((current) => current.map((plan) => plan.date === date ? savedPlan : plan))
                        setDrafts((current) => ({...current, [date]: savedPlan.items}))
                        setDirtyDates((dates) => {
                            const next = new Set(dates)
                            next.delete(date)
                            return next
                        })
                    })
                    .catch((error: unknown) => {
                        const apiMessage = typeof error === 'object' && error !== null
                            ? (error as ApiError).message
                            : undefined
                        setMessage(apiMessage ?? '계획을 자동 저장하지 못했습니다. 변경 내용을 확인해 주세요.')
                    })
                    .finally(() => {
                        savingDatesRef.current.delete(date)
                        if ((revisionRef.current.get(date) ?? 0) !== revision) {
                            setDirtyDates((dates) => new Set(dates))
                        }
                    })
            })
        }, 500)
        return () => window.clearTimeout(timeoutId)
    }, [dirtyDates, drafts])

    const items = drafts[selectedDate] ?? []
    const todayTasks = drafts[today] ?? plans.find((plan) => plan.date === today)?.items ?? []
    const calendarDays = useMemo(() => monthDays(visibleMonth), [visibleMonth])

    const markDirty = (date: string) => {
        revisionRef.current.set(date, (revisionRef.current.get(date) ?? 0) + 1)
        setDirtyDates((dates) => new Set(dates).add(date))
        setMessage(null)
    }

    const selectDate = (date: string) => {
        const nextMonth = parseDate(date)
        setSelectedDate(date)
        setVisibleMonth(nextMonth)
        setMessage(null)
    }

    const moveItem = (index: number, offset: number) => {
        const target = index + offset
        if (target < 0 || target >= items.length) return
        setDrafts((current) => {
            const next = [...(current[selectedDate] ?? [])]
            ;[next[index], next[target]] = [next[target], next[index]]
            return {...current, [selectedDate]: next}
        })
        markDirty(selectedDate)
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

    const replaceAcrossPlans = (field: 'title' | 'status', item: DailyPlanItem, value: string) => {
        const replace = (candidate: DailyPlanItem) => candidate.taskId === item.taskId ? {...candidate, [field]: value} : candidate
        setDrafts((current) => Object.fromEntries(Object.entries(current).map(([date, dateItems]) => [date, dateItems.map(replace)])))
        setPlans((current) => current.map((plan) => ({...plan, items: plan.items.map(replace)})))
    }

    const changeTaskTitle = async (item: DailyPlanItem, title: string) => {
        await updateTask(item.taskId, {title, status: item.status})
        replaceAcrossPlans('title', item, title)
    }

    const changeTaskStatus = async (item: DailyPlanItem, nextStatus: TaskStatus) => {
        setPendingTaskId(item.taskId)
        setMessage(null)
        try {
            await updateTask(item.taskId, {title: item.title, status: nextStatus})
            replaceAcrossPlans('status', item, nextStatus)
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? 'Task 상태를 변경하지 못했습니다. 다시 시도해 주세요.')
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
        return apiError?.errors?.title ?? apiError?.message ?? 'Task 제목을 저장하지 못했습니다.'
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
                            {items.map((item, index) => (
                                <li className={styles.todoCard} key={item.id}>
                                    <span className={styles.todoIdentity}>
                                        <button
                                            type="button"
                                            className={`${styles.checkmark} ${item.status === 'DONE' ? styles.checked : ''}`}
                                            aria-label={`${item.title} ${item.status === 'DONE' ? '완료 취소' : '완료 처리'}`}
                                            onClick={() => void changeTaskStatus(item, item.status === 'DONE' ? 'TODO' : 'DONE')}
                                        />
                                        <span className={styles.todoCopy}>
                                            {item.projectName && <span className={styles.projectName}>{item.projectName}</span>}
                                            <InlineEditableText
                                                value={item.title}
                                                ariaLabel="Task 제목"
                                                maxLength={255}
                                                requiredMessage="Task 제목을 입력해 주세요."
                                                onSave={(title) => changeTaskTitle(item, title)}
                                                getErrorMessage={getTaskTitleError}
                                            />
                                        </span>
                                    </span>
                                    <div className={styles.todoActions}>
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
                                        <DailyPlanCardMenu label={`${item.title} 카드 메뉴`}>
                                            {selectedDate === today && (
                                                <ModalTriggerButton dialogId="create-session-dialog" isOpen={sessionTaskId === item.taskId} variant="plain" icon={<IconPlayerPlay size={15} aria-hidden="true" />} onClick={() => setSessionTaskId(item.taskId)}>
                                                    다이브 세션
                                                </ModalTriggerButton>
                                            )}
                                            <button type="button" disabled={index === 0} onClick={() => moveItem(index, -1)}>위로</button>
                                            <button type="button" disabled={index === items.length - 1} onClick={() => moveItem(index, 1)}>아래로</button>
                                            <DeleteIconButton label="계획에서 제거" iconSize={15} onClick={() => void removeItem(item.id)} />
                                        </DailyPlanCardMenu>
                                    </div>
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
