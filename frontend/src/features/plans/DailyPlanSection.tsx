import {useEffect, useMemo, useRef, useState} from 'react'
import {
    IconArrowDown,
    IconArrowUp,
    IconCalendar,
    IconLoader2,
    IconPlayerPlay,
    IconPlus,
    IconTrash
} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ActionButton from '../../components/ActionButton'
import InlineEditableText from '../../components/InlineEditableText'
import {useNavigate} from 'react-router-dom'
import type {Project, ProjectDetail} from '../projects/projectTypes'
import CreateSessionModal from '../sessions/CreateSessionModal'
import {updateTask} from '../tasks/taskApi'
import {TASK_STATUS_LABEL, TASK_STATUS_VALUES} from '../tasks/taskLabels'
import {
    addDailyPlanItems,
    deleteDailyPlanItem,
    getDailyPlans,
    reorderDailyPlanItems,
    updateDailyPlanItem
} from './dailyPlanApi'
import type {TaskStatus} from '../tasks/taskTypes'
import type {DailyPlan, DailyPlanItem} from './dailyPlanTypes'
import DailyPlanCardMenu from './DailyPlanCardMenu'
import TaskPickerModal from './TaskPickerModal'
import styles from './DailyPlanSection.module.css'

interface DailyPlanSectionProps {
    projects: Project[]
}

function formatLocalDate(date: Date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}

function addDays(value: string, amount: number) {
    const date = new Date(`${value}T00:00:00`)
    date.setDate(date.getDate() + amount)
    return formatLocalDate(date)
}

const dayFormatter = new Intl.DateTimeFormat('ko-KR', {month: 'short', day: 'numeric'})
const weekdayFormatter = new Intl.DateTimeFormat('ko-KR', {weekday: 'short'})
const rangeFormatter = new Intl.DateTimeFormat('ko-KR', {month: 'short', day: 'numeric'})

export default function DailyPlanSection({projects}: DailyPlanSectionProps) {
    const navigate = useNavigate()
    const today = useMemo(() => formatLocalDate(new Date()), [])
    const [fromDate, setFromDate] = useState(today)
    const [toDate, setToDate] = useState(addDays(today, 6))
    const [selectedDate, setSelectedDate] = useState(today)
    const [plans, setPlans] = useState<DailyPlan[]>([])
    const [drafts, setDrafts] = useState<Record<string, DailyPlanItem[]>>({})
    const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
    const [dirtyDates, setDirtyDates] = useState<Set<string>>(new Set())
    const [message, setMessage] = useState<string | null>(null)
    const [isPickerOpen, setIsPickerOpen] = useState(false)
    const [sessionTaskId, setSessionTaskId] = useState<number | null>(null)
    const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
    const planRevisionRef = useRef(new Map<string, number>())
    const savingDatesRef = useRef(new Set<string>())

    useEffect(() => {
        let active = true
        void getDailyPlans(fromDate, toDate)
            .then((response) => {
                if (!active) return
                setPlans(response)
                setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
                setDirtyDates(new Set())
                planRevisionRef.current.clear()
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

    useEffect(() => {
        if (dirtyDates.size === 0) return

        const timeoutId = window.setTimeout(() => {
            dirtyDates.forEach((date) => {
                if (savingDatesRef.current.has(date)) return

                const items = drafts[date] ?? []
                const revision = planRevisionRef.current.get(date) ?? 0
                savingDatesRef.current.add(date)

                void reorderDailyPlanItems(date, {itemIds: items.map((item) => item.id)})
                    .then((savedPlan) => {
                        if ((planRevisionRef.current.get(date) ?? 0) !== revision) return

                        setPlans((current) => current.map((plan) => plan.date === date
                            ? savedPlan
                            : plan))
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
                        if ((planRevisionRef.current.get(date) ?? 0) !== revision) {
                            setDirtyDates((dates) => new Set(dates))
                        }
                    })
            })
        }, 500)

        return () => window.clearTimeout(timeoutId)
    }, [dirtyDates, drafts])

    const retry = async () => {
        setStatus('loading')
        try {
            const response = await getDailyPlans(fromDate, toDate)
            setPlans(response)
            setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
            setDirtyDates(new Set())
            planRevisionRef.current.clear()
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

    const markDateDirty = (date: string) => {
        planRevisionRef.current.set(date, (planRevisionRef.current.get(date) ?? 0) + 1)
        setDirtyDates((dates) => new Set(dates).add(date))
        setMessage(null)
    }

    const moveItem = (index: number, offset: number) => {
        const target = index + offset
        if (target < 0 || target >= draftItems.length) return
        setDrafts((current) => {
            const items = current[selectedDate] ?? []
            const next = [...items]
            ;[next[index], next[target]] = [next[target], next[index]]
            return {...current, [selectedDate]: next}
        })
        markDateDirty(selectedDate)
    }

    const replacePlan = (savedPlan: DailyPlan) => {
        setPlans((current) => current.map((plan) => plan.date === savedPlan.date ? savedPlan : plan))
        setDrafts((current) => ({...current, [savedPlan.date]: savedPlan.items}))
    }

    const addTasks = async (tasks: ProjectDetail['tasks']) => {
        if (tasks.length === 0) return
        replacePlan(await addDailyPlanItems(selectedDate, {taskIds: tasks.map((task) => task.id)}))
    }

    const addAdHoc = async (title: string, projectId: number | null) => {
        replacePlan(await addDailyPlanItems(selectedDate, {
            title,
            ...(projectId === null ? {} : {projectId}),
        }))
    }

    const changeTaskTitle = async (date: string, item: DailyPlanItem, title: string) => {
        if (item.taskId === null) {
            replacePlan(await updateDailyPlanItem(date, item.id, title))
            return
        }

        await updateTask(item.taskId, {
            title,
            status: item.status!,
        })

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
        if (item.taskId === null) return

        setPendingTaskId(item.taskId)
        setMessage(null)

        try {
            await updateTask(item.taskId, {title: item.title, status})

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
            setMessage(apiMessage ?? 'Task 상태를 변경하지 못했습니다. 다시 시도해 주세요.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const removeItem = async (date: string, itemId: number) => {
        try {
            await deleteDailyPlanItem(date, itemId)
            const remove = (items: DailyPlanItem[]) => items
                .filter((item) => item.id !== itemId)
                .map((item, orderIdx) => ({...item, orderIdx}))
            setDrafts((current) => ({...current, [date]: remove(current[date] ?? [])}))
            setPlans((current) => current.map((plan) => plan.date === date
                ? {...plan, items: remove(plan.items)}
                : plan))
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '계획에서 할 일을 제거하지 못했습니다.')
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

    return (
        <section className={styles.section} aria-labelledby="daily-plan-title">
            <header className={styles.heading}>
                <div className={styles['title-group']}>
                    <h2 id="daily-plan-title">오늘의 계획</h2>
                    <div className={styles['database-toolbar']}>
                        <details className={styles.range}>
                            <summary aria-label="계획 조회 기간 변경">
                                <IconCalendar size={16} aria-hidden="true"/>
                                {rangeFormatter.format(new Date(`${fromDate}T00:00:00`))}–{rangeFormatter.format(new Date(`${toDate}T00:00:00`))}
                            </summary>
                            <div className={styles['range-popover']} aria-label="계획 조회 기간">
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
                <div className={styles.state} role="status"><IconLoader2 className={styles.spinner} size={19}/>계획을 불러오는
                    중…
                </div>
            ) : status === 'error' ? (
                <div className={styles.state}><p>계획을 불러오지 못했습니다.</p>
                    <button type="button" onClick={() => void retry()}>다시 불러오기</button>
                </div>
            ) : (
                <>
                    <div className={styles['board-scroll']}>
                        <div className={styles.board} role="tablist" aria-label="날짜별 계획 보드">
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
                                            {items.map((item, index) => (
                                                <li className={`${styles.card} ${item.projectId === null ? styles['ad-hoc-card'] : styles[`project-tone-${item.projectId % 4}`]}`}
                                                    key={item.id}>
                                                    <div className={styles['card-select']}
                                                         onClick={() => setSelectedDate(plan.date)}>
                                                        {item.projectName !== null && (
                                                            <button type="button" className={styles['project-label']}
                                                                    aria-label={`${item.title}이 있는 ${dayFormatter.format(date)} 선택`}>
                                                                <span className={styles['project-mark']}
                                                                      aria-hidden="true"/>{item.projectName}
                                                            </button>
                                                        )}
                                                        <strong>
                                                            <InlineEditableText
                                                                value={item.title}
                                                                ariaLabel="Task 제목"
                                                                maxLength={255}
                                                                requiredMessage="Task 제목을 입력해 주세요."
                                                                onSave={(title) => changeTaskTitle(plan.date, item, title)}
                                                                getErrorMessage={getTaskTitleError}
                                                            />
                                                        </strong>
                                                        {item.status !== null && (
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
                                                        )}
                                                    </div>
                                                    {selected && (
                                                        <DailyPlanCardMenu
                                                            label={`${item.title} 카드 메뉴`}>
                                                                {plan.date === today && item.taskId !== null && (
                                                                    <ActionButton
                                                                        variant="plain"
                                                                        icon={<IconPlayerPlay size={15}
                                                                                              aria-hidden="true"/>}
                                                                        onClick={() => setSessionTaskId(item.taskId)}
                                                                    >
                                                                        다이브 세션
                                                                    </ActionButton>
                                                                )}
                                                                <button type="button" disabled={index === 0}
                                                                        onClick={() => moveItem(index, -1)}><IconArrowUp
                                                                    size={15} aria-hidden="true"/>위로
                                                                </button>
                                                                <button type="button"
                                                                        disabled={index === items.length - 1}
                                                                        onClick={() => moveItem(index, 1)}>
                                                                    <IconArrowDown size={15} aria-hidden="true"/>아래로
                                                                </button>
                                                                <button type="button" aria-label="계획에서 제거"
                                                                        onClick={() => void removeItem(plan.date, item.id)}>
                                                                    <IconTrash size={15} aria-hidden="true"/></button>
                                                        </DailyPlanCardMenu>
                                                    )}
                                                </li>
                                            ))}
                                        </ol>
                                        {items.length === 0 && <p className={styles.empty}>아직 계획된 할 일이 없습니다.</p>}
                                        <button type="button" className={styles['column-add']} onClick={() => {
                                            setSelectedDate(plan.date);
                                            setIsPickerOpen(true)
                                        }}><IconPlus size={15} aria-hidden="true"/></button>
                                    </section>
                                )
                            })}
                        </div>
                    </div>
                </>
            )}

            {isPickerOpen && <TaskPickerModal projects={projects}
                                              selectedTaskIds={new Set(draftItems.flatMap((item) => item.taskId === null ? [] : [item.taskId]))}
                                              onAdd={addTasks} onAddAdHoc={addAdHoc}
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
