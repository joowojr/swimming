import {useEffect, useMemo, useState} from 'react'
import {
    IconCalendar,
    IconLoader2,
    IconPlus
} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import InlineEditableText from '../../components/InlineEditableText'
import TaskMenu, { TaskFlagMenuItems } from '../../components/TaskMenu'
import {useNavigate} from 'react-router-dom'
import CreateSessionModal from '../sessions/CreateSessionModal'
import {updateTaskPriority, updateTaskStatus, updateTaskTitle, updateTaskUrgent} from '../tasks/taskApi'
import {TASK_STATUS_LABEL, TASK_STATUS_VALUES} from '../tasks/taskLabels'
import {
    addDailyPlanItems,
    deleteDailyPlanItem,
    getDailyPlans,
} from './dailyPlanApi'
import type {TaskResponse, TaskStatus, TaskSummaryResponse} from '../tasks/taskTypes'
import type {DailyPlan, DailyPlanItem} from './dailyPlanTypes'
import TaskPickerModal from './TaskPickerModal'
import styles from './DailyPlanBoard.module.css'
import {formatLocalDate, parseLocalDate} from '../../lib/date'

function addDays(value: string, amount: number) {
    const date = parseLocalDate(value)
    date.setDate(date.getDate() + amount)
    return formatLocalDate(date)
}

const dayFormatter = new Intl.DateTimeFormat('ko-KR', {month: 'short', day: 'numeric'})
const weekdayFormatter = new Intl.DateTimeFormat('ko-KR', {weekday: 'short'})
const rangeFormatter = new Intl.DateTimeFormat('ko-KR', {month: 'short', day: 'numeric'})

export default function DailyPlanBoard() {
    const navigate = useNavigate()
    const today = useMemo(() => formatLocalDate(new Date()), [])
    const [fromDate, setFromDate] = useState(today)
    const [toDate, setToDate] = useState(addDays(today, 6))
    const [selectedDate, setSelectedDate] = useState(today)
    const [plans, setPlans] = useState<DailyPlan[]>([])
    const [drafts, setDrafts] = useState<Record<string, DailyPlanItem[]>>({})
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [message, setMessage] = useState<string | null>(null)
    const [isPickerOpen, setIsPickerOpen] = useState(false)
    const [sessionTaskId, setSessionTaskId] = useState<number | null>(null)
    const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)

    useEffect(() => {
        let active = true
        void getDailyPlans(fromDate, toDate)
            .then((response) => {
                if (!active) return
                setPlans(response)
                setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
                setStatus('ready')
            })
            .catch(() => {
                if (active) setStatus('error')
            })
        return () => {
            active = false
        }
    }, [fromDate, toDate])

    const draftItems = drafts[selectedDate] ?? []
    const todayTasks = drafts[today] ?? plans.find((plan) => plan.date === today)?.items ?? []

    const retry = async () => {
        setStatus('loading')
        try {
            const response = await getDailyPlans(fromDate, toDate)
            setPlans(response)
            setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
            setStatus('ready')
        } catch {
            setStatus('error')
        }
    }

    const setRange = (nextFrom: string, nextTo: string) => {
        setStatus('loading')
        setMessage(null)
        setFromDate(nextFrom)
        setToDate(nextTo)
        if (selectedDate < nextFrom || selectedDate > nextTo) setSelectedDate(nextFrom)
    }

    const changeFromDate = (value: string) => {
        const boundedTo = value > toDate || toDate > addDays(value, 6) ? addDays(value, 6) : toDate
        setRange(value, boundedTo)
    }

    const changeToDate = (value: string) => setRange(fromDate, value)

    const replacePlan = (savedPlan: DailyPlan) => {
        setPlans((current) => current.map((plan) => plan.date === savedPlan.date ? savedPlan : plan))
        setDrafts((current) => ({...current, [savedPlan.date]: savedPlan.items}))
    }

    const addTasks = async (tasks: TaskSummaryResponse[]) => {
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

    const changeTaskTitle = async (item: DailyPlanItem, title: string) => {
        await updateTaskTitle(item.taskId, {title})

        const replaceTitle = (items: DailyPlanItem[]) => items.map((candidate) => (
            candidate.taskId === item.taskId ? {...candidate, title} : candidate
        ))
        setDrafts((current) => Object.fromEntries(
            Object.entries(current).map(([date, items]) => [date, replaceTitle(items)]),
        ))
        setPlans((current) => current.map((plan) => ({
            ...plan,
            items: replaceTitle(plan.items),
        })))
    }

    const changeTaskStatus = async (item: DailyPlanItem, status: TaskStatus) => {
        setPendingTaskId(item.taskId)
        setMessage(null)

        try {
            await updateTaskStatus(item.taskId, {status})

            // 같은 Task가 여러 날짜에 담겨 있을 수 있어 전 날짜에 반영한다.
            const replaceStatus = (items: DailyPlanItem[]) => items.map((candidate) => (
                candidate.taskId === item.taskId ? {...candidate, status} : candidate
            ))
            setDrafts((current) => Object.fromEntries(
                Object.entries(current).map(([date, items]) => [date, replaceStatus(items)]),
            ))
            setPlans((current) => current.map((plan) => ({
                ...plan,
                items: replaceStatus(plan.items),
            })))
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null
                ? (error as ApiError).message
                : undefined
            setMessage(apiMessage ?? '상태를 변경하지 못했습니다. 다시 시도해 주세요.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const replaceTaskFlags = (task: Pick<TaskResponse, 'id' | 'priority' | 'urgent'>) => {
        const replace = (items: DailyPlanItem[]) => items.map((candidate) => (
            candidate.taskId === task.id
                ? {...candidate, priority: task.priority, urgent: task.urgent}
                : candidate
        ))
        setDrafts((current) => Object.fromEntries(
            Object.entries(current).map(([date, items]) => [date, replace(items)]),
        ))
        setPlans((current) => current.map((plan) => ({
            ...plan,
            items: replace(plan.items),
        })))
    }

    const changeTaskPriority = async (item: DailyPlanItem) => {
        setPendingTaskId(item.taskId)
        setMessage(null)
        try {
            replaceTaskFlags(await updateTaskPriority(item.taskId, {priority: !item.priority}))
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
            replaceTaskFlags(await updateTaskUrgent(item.taskId, {urgent: !item.urgent}))
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '즉시 표시를 변경하지 못했습니다.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const removeItem = async (date: string, itemId: number) => {
        try {
            await deleteDailyPlanItem(date, itemId)
            const remove = (items: DailyPlanItem[]) => items
                .filter((item) => item.id !== itemId)
            setDrafts((current) => ({...current, [date]: remove(current[date] ?? [])}))
            setPlans((current) => current.map((plan) => plan.date === date
                ? {...plan, items: remove(plan.items)}
                : plan))
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '캘린더에서 할 일을 제거하지 못했습니다.')
        }
    }

    const getTaskTitleError = (error: unknown) => {
        const apiError = typeof error === 'object' && error !== null
            ? error as ApiError
            : undefined
        return apiError?.errors?.title
            ?? apiError?.message
            ?? '할 일 제목을 저장하지 못했습니다.'
    }

    return (
        <section className={styles.section} aria-labelledby="daily-plan-title">
            <header className={styles.heading}>
                <div className={styles['title-group']}>
                    <h2 id="daily-plan-title">오늘의 캘린더</h2>
                    <div className={styles['database-toolbar']}>
                        <details className={styles.range}>
                            <summary aria-label="캘린더 조회 기간 변경">
                                <IconCalendar size={16} aria-hidden="true"/>
                                {rangeFormatter.format(new Date(`${fromDate}T00:00:00`))}–{rangeFormatter.format(new Date(`${toDate}T00:00:00`))}
                            </summary>
                            <div className={styles['range-popover']} aria-label="캘린더 조회 기간">
                                <p>시작일을 포함해 최대 7일까지 볼 수 있습니다.</p>
                                <label><span>시작일</span><input type="date" value={fromDate}
                                                              onChange={(event) => changeFromDate(event.target.value)}/></label>
                                <label><span>종료일</span><input type="date" value={toDate} min={fromDate}
                                                              max={addDays(fromDate, 6)}
                                                              onChange={(event) => changeToDate(event.target.value)}/></label>
                            </div>
                        </details>
                        {message && <p className={styles['save-status']} role="alert">{message}</p>}
                    </div>
                </div>
            </header>

            {status === 'loading' ? (
                <div className={styles.state} role="status"><IconLoader2 className={styles.spinner} size={19}/>캘린더을 불러오는
                    중…
                </div>
            ) : status === 'error' ? (
                <div className={styles.state}><p>캘린더을 불러오지 못했습니다.</p>
                    <button type="button" onClick={() => void retry()}>다시 불러오기</button>
                </div>
            ) : (
                <>
                    <div className={styles['board-scroll']}>
                        <div className={styles.board} role="tablist" aria-label="날짜별 캘린더 보드">
                            {plans.map((plan) => {
                                const date = new Date(`${plan.date}T00:00:00`)
                                const items = drafts[plan.date] ?? plan.items
                                const selected = selectedDate === plan.date
                                return (
                                    <section className={styles.column} data-selected={selected} key={plan.date}>
                                        <button
                                            type="button"
                                            className={styles['column-header']}
                                            role="tab"
                                            aria-selected={selected}
                                            onClick={() => {
                                                setSelectedDate(plan.date);
                                                setMessage(null)
                                            }}
                                        >
                                            <span
                                                className={styles['date-name']}><strong>{weekdayFormatter.format(date)}</strong>{dayFormatter.format(date)}</span>
                                            <span className={styles.count}>{items.length}</span>
                                        </button>
                                        <ol className={styles.list}>
                                            {items.map((item) => (
                                                <li className={`${styles.card} ${item.itemType === 'AD_HOC' ? styles['ad-hoc-card'] : styles[`project-tone-${item.folderId % 4}`]}`}
                                                    key={item.id}>
                                                    <div className={styles['card-select']}
                                                         onClick={() => setSelectedDate(plan.date)}>
                                                        {item.folderName !== null && (
                                                            <button type="button" className={styles['project-label']}
                                                                    aria-label={`${item.title}이 있는 ${dayFormatter.format(date)} 선택`}>
                                                                <span className={styles['project-mark']}
                                                                      aria-hidden="true"/>{item.folderName}
                                                            </button>
                                                        )}
                                                        <strong>
                                                            <InlineEditableText
                                                                wrap
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
                                                        </strong>
                                                        <span className={styles.meta}>
                                                                <select
                                                                    className={styles.status}
                                                                    data-status={item.status}
                                                                    value={item.status}
                                                                    aria-label={`${item.title} 상태`}
                                                                    disabled={pendingTaskId === item.taskId}
                                                                    onClick={(event) => event.stopPropagation()}
                                                                    onChange={(event) => void changeTaskStatus(item, event.target.value as TaskStatus)}
                                                                >
                                                                    {TASK_STATUS_VALUES.map((status) => (
                                                                        <option value={status} key={status}>{TASK_STATUS_LABEL[status]}</option>
                                                                    ))}
                                                                </select>
                                                        </span>
                                                    </div>
                                                    {selected && (
                                                        <TaskMenu
                                                            label={`${item.title} 카드 메뉴`}>
                                                                <TaskFlagMenuItems
                                                                    priority={item.priority}
                                                                    urgent={item.urgent}
                                                                    disabled={pendingTaskId === item.taskId}
                                                                    onTogglePriority={() => void changeTaskPriority(item)}
                                                                    onToggleUrgent={() => void changeTaskUrgent(item)}
                                                                    session={plan.date === today ? {
                                                                        onStart: () => setSessionTaskId(item.taskId),
                                                                        dialogId: 'create-session-dialog',
                                                                        isOpen: sessionTaskId === item.taskId,
                                                                    } : undefined}
                                                                    onDelete={() => void removeItem(plan.date, item.id)}
                                                                />
                                                        </TaskMenu>
                                                    )}
                                                </li>
                                            ))}
                                        </ol>
                                        {items.length === 0 && <p className={styles.empty}>아직 캘린더된 할 일이 없습니다.</p>}
                                        <ModalTriggerButton
                                            className={styles['column-add']}
                                            dialogId="task-picker-dialog"
                                            variant="plain"
                                            aria-label={`${dayFormatter.format(date)} 캘린더에 할 일 추가`}
                                            icon={<IconPlus size={15} aria-hidden="true"/>}
                                            onClick={() => {
                                            setSelectedDate(plan.date);
                                            setIsPickerOpen(true)
                                        }} />
                                    </section>
                                )
                            })}
                        </div>
                    </div>
                </>
            )}

            {isPickerOpen && <TaskPickerModal
                initialPlanDate={selectedDate}
                                              selectedTaskIds={new Set(draftItems.map((item) => item.taskId))}
                                              onAdd={addTasks} onAddTask={addTask}
                                              onClose={() => setIsPickerOpen(false)}/>}
            {sessionTaskId !== null && (
                <CreateSessionModal
                    todayTasks={todayTasks}
                    initialTaskId={sessionTaskId}
                    onClose={() => setSessionTaskId(null)}
                    onStarted={(session) => {
                        setSessionTaskId(null)
                        navigate(`/sessions/${session.id}`)
                    }}
                />
            )}
        </section>
    )
}
