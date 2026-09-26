import {useEffect, useMemo, useState} from 'react'
import {
    IconChevronLeft,
    IconChevronRight,
    IconLoader2,
} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ModeToggle from '../../components/ModeToggle'
import InlineEditableText from '../../components/InlineEditableText'
import ChecklistCard from '../../components/ChecklistCard'
import TaskMenu, { TaskFlagMenuItems } from '../../components/TaskMenu'
import {useNavigate} from 'react-router-dom'
import CreateSessionModal from '../sessions/CreateSessionModal'
import {updateTaskStatus, updateTaskTitle} from '../tasks/taskApi'
import {TASK_STATUS_LABEL, TASK_STATUS_VALUES} from '../tasks/taskLabels'
import type {TaskStatus} from '../tasks/taskTypes'
import type {DailyPlanItem} from './dailyPlanTypes'
import {formatLocalDate, parseLocalDate} from '../../lib/date'
import {monthRange} from './planDate'
import {joinPlanItems} from './planItems'
import {useDailyPlanStore} from '../../store/dailyPlanStore'
import {useHolidayStore} from '../../store/holidayStore'
import {useFolderStore} from '../../store/folderStore.ts'
import {usePinboardViewStore} from '../../store/pinboardViewStore'
import {useTaskStore} from '../../store/taskStore'
import TaskPickerModal from './TaskPickerModal'
import type { TaskPickerSubmission } from './TaskPickerModal'
import TaskInfoModal from '../tasks/TaskInfoModal'
import styles from './DailyPlanner.module.css'

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {year: 'numeric', month: 'long'})
const selectedDateFormatter = new Intl.DateTimeFormat('ko-KR', {month: 'long', day: 'numeric', weekday: 'long'})
const monthDayFormatter = new Intl.DateTimeFormat('ko-KR', {month: 'long', day: 'numeric'})
const dayOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {day: 'numeric'})
const fullDateFormatter = new Intl.DateTimeFormat('ko-KR', {year: 'numeric', month: 'long', day: 'numeric'})
const dayLabels = ['일', '월', '화', '수', '목', '금', '토']

const CALENDAR_VIEW_OPTIONS = [
    {value: 'week', label: '주'},
    {value: 'month', label: '월'},
] as const

const WEEK_DAYS = 7


function startOfMonth(date: Date) {
    return new Date(date.getFullYear(), date.getMonth(), 1)
}

function startOfWeek(date: Date) {
    const start = new Date(date.getFullYear(), date.getMonth(), date.getDate())
    start.setDate(start.getDate() - start.getDay())
    return start
}

function weekDays(anchor: Date) {
    const start = startOfWeek(anchor)
    return Array.from({length: WEEK_DAYS}, (_, index) => (
        new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    ))
}

function formatDayRange(days: (Date | null)[]) {
    const visibleDays = days.filter((date): date is Date => date !== null)
    const first = visibleDays[0]
    const last = visibleDays[visibleDays.length - 1]
    if (!first || !last) return ''
    if (first.getFullYear() !== last.getFullYear()) {
        return `${fullDateFormatter.format(first)} – ${fullDateFormatter.format(last)}`
    }
    if (first.getMonth() !== last.getMonth()) {
        return `${monthDayFormatter.format(first)} – ${monthDayFormatter.format(last)}`
    }
    return `${monthDayFormatter.format(first)} – ${dayOnlyFormatter.format(last)}`
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

interface DailyPlannerProps {
    /**
     * 할 일 추가 모달의 열림 상태. 여는 버튼은 핀보드의 보기 전환 줄에 있어서, 그 상태만
     * 위에서 받는다. 날짜와 목록은 여전히 이 컴포넌트가 소유한다 — 모달이 무엇을 담을지는
     * 어느 날짜를 보고 있는지가 정하고, 그건 여기밖에 모른다.
     */
    isPickerOpen: boolean
    onPickerClose: () => void
}

export default function DailyPlanner({isPickerOpen, onPickerClose}: DailyPlannerProps) {
    const navigate = useNavigate()
    const today = useMemo(() => formatLocalDate(new Date()), [])
    const [selectedDate, setSelectedDate] = useState(today)
    const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(parseLocalDate(today)))
    const calendarView = usePinboardViewStore((state) => state.calendarView)
    const setCalendarView = usePinboardViewStore((state) => state.setCalendarView)
    const [message, setMessage] = useState<string | null>(null)
    const [sessionTaskId, setSessionTaskId] = useState<number | null>(null)
    const [pendingTaskId, setPendingTaskId] = useState<number | null>(null)
    const [moveTarget, setMoveTarget] = useState<DailyPlanItem | null>(null)

    const entriesByDate = useDailyPlanStore((state) => state.entriesByDate)
    const loadedMonths = useDailyPlanStore((state) => state.loadedMonths)
    const status = useDailyPlanStore((state) => state.status)
    const loadMonth = useDailyPlanStore((state) => state.loadMonth)
    const addItems = useDailyPlanStore((state) => state.addItems)
    const removePlanTask = useDailyPlanStore((state) => state.removeTask)
    const tasksById = useTaskStore((state) => state.byId)
    const upsertTasks = useTaskStore((state) => state.upsert)
    const folders = useFolderStore((state) => state.folders)
    const holidayNamesByDate = useHolidayStore((state) => state.namesByDate)
    const loadHolidayMonth = useHolidayStore((state) => state.loadMonth)

    const calendarDays = useMemo(
        () => (calendarView === 'week'
            ? weekDays(parseLocalDate(selectedDate))
            : monthDays(visibleMonth)),
        [calendarView, selectedDate, visibleMonth],
    )

    const {fromDate, toDate} = useMemo(() => monthRange(visibleMonth), [visibleMonth])
    const visibleMonthKey = fromDate.slice(0, 7)

    useEffect(() => {
        // 이미 받아둔 달이면 다시 조회하지 않는다. 실패는 store가 status로 알린다.
        if (loadedMonths.has(visibleMonthKey)) return
        void loadMonth(visibleMonthKey, fromDate, toDate).catch(() => {})
    }, [visibleMonthKey, fromDate, toDate, loadMonth, loadedMonths])

    // 주간 화면이 월 경계를 넘으면 두 달이 보인다. 보이는 날짜에서 달을 뽑아 그만큼 조회한다.
    const visibleHolidayMonths = useMemo(
        () => [...new Set(calendarDays
            .filter((date): date is Date => date !== null)
            .map((date) => formatLocalDate(date).slice(0, 7)))],
        [calendarDays],
    )

    useEffect(() => {
        for (const monthKey of visibleHolidayMonths) {
            void loadHolidayMonth(monthKey)
        }
    }, [visibleHolidayMonths, loadHolidayMonth])

    const selectedHolidayNames = holidayNamesByDate[selectedDate] ?? []

    // 캘린더 항목(멤버십) + task(가변 속성) + 폴더(이름)를 여기서 합친다.
    const items = useMemo(
        () => joinPlanItems(entriesByDate[selectedDate] ?? [], tasksById, folders),
        [entriesByDate, selectedDate, tasksById, folders],
    )
    const todayTasks = useMemo(
        () => joinPlanItems(entriesByDate[today] ?? [], tasksById, folders),
        [entriesByDate, today, tasksById, folders],
    )
    const isLoading = status === 'idle' || status === 'loading'

    const selectDate = (date: string) => {
        setSelectedDate(date)
        // 같은 달 안에서 날짜만 옮기면 visibleMonth를 그대로 두어 재조회를 막는다.
        setVisibleMonth((current) => {
            const next = startOfMonth(parseLocalDate(date))
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

    const moveWeek = (amount: number) => {
        const current = parseLocalDate(selectedDate)
        const next = new Date(
            current.getFullYear(),
            current.getMonth(),
            current.getDate() + amount * WEEK_DAYS,
        )
        selectDate(formatLocalDate(next))
    }

    /** 모달에서 담은 것을 한 번에 캘린더에 넣는다. 이미 있는 할 일과 새로 만들 할 일은 경로가 다르다. */
    /** 모달에서 고른 날짜에 담는다. 날짜가 필수인 모달이라 날짜 없이 제출되지 않는다. */
    const addPickedTasks = async ({existingTasks, newTasks, planDate}: TaskPickerSubmission) => {
        if (!planDate) return
        if (existingTasks.length > 0) {
            await addItems(planDate, {taskIds: existingTasks.map((task) => task.taskId)})
        }
        if (newTasks.length > 0) {
            await addItems(planDate, {
                tasks: newTasks.map(({title, folderId, priority, urgent}) => ({
                    title,
                    priority,
                    urgent,
                    ...(folderId === null ? {} : {folderId}),
                })),
            })
        }
    }

    const changeTaskTitle = async (item: DailyPlanItem, title: string) => {
        upsertTasks([await updateTaskTitle(item.taskId, {title})])
    }

    const changeTaskStatus = async (item: DailyPlanItem, nextStatus: TaskStatus) => {
        setPendingTaskId(item.taskId)
        setMessage(null)
        try {
            upsertTasks([await updateTaskStatus(item.taskId, {status: nextStatus})])
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '상태를 변경하지 못했습니다. 다시 시도해 주세요.')
        } finally {
            setPendingTaskId(null)
        }
    }

    const removeItem = async (taskId: number) => {
        try {
            await removePlanTask(selectedDate, taskId)
        } catch (error: unknown) {
            const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
            setMessage(apiMessage ?? '캘린더에서 할 일을 제거하지 못했습니다.')
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
                    <div className={styles.calendarNav}>
                        <button
                            type="button"
                            aria-label={calendarView === 'week' ? '이전 주' : '이전 달'}
                            onClick={() => (calendarView === 'week' ? moveWeek(-1) : moveMonth(-1))}
                        >
                            <IconChevronLeft size={18} aria-hidden="true" />
                        </button>
                        <h2>
                            {calendarView === 'week'
                                ? formatDayRange(calendarDays)
                                : dateFormatter.format(visibleMonth)}
                        </h2>
                        <button
                            type="button"
                            aria-label={calendarView === 'week' ? '다음 주' : '다음 달'}
                            onClick={() => (calendarView === 'week' ? moveWeek(1) : moveMonth(1))}
                        >
                            <IconChevronRight size={18} aria-hidden="true" />
                        </button>
                    </div>
                    <ModeToggle
                        className={styles.calendarViewToggle}
                        ariaLabel="캘린더 보기 단위"
                        options={CALENDAR_VIEW_OPTIONS}
                        value={calendarView}
                        onChange={setCalendarView}
                    />
                </header>
                <div className={styles.weekdays} aria-hidden="true">
                    {dayLabels.map((label) => <span key={label}>{label}</span>)}
                </div>
                <div className={styles.calendarGrid} role="grid" aria-label="캘린더 날짜 선택">
                    {calendarDays.map((date, index) => {
                        if (!date) return <span className={styles.outsideDay} key={`empty-${index}`} aria-hidden="true" />
                        const dateValue = formatLocalDate(date)
                        const selected = dateValue === selectedDate
                        const isToday = dateValue === today
                        const dateItemCount = (entriesByDate[dateValue] ?? []).length
                        const holidayNames = holidayNamesByDate[dateValue] ?? []
                        const holidayLabel = holidayNames.join(', ')
                        return (
                            <button
                                type="button"
                                role="gridcell"
                                className={styles.day}
                                data-selected={selected}
                                data-today={isToday}
                                data-holiday={holidayNames.length > 0 || undefined}
                                aria-selected={selected}
                                // 색만으로는 공휴일이 전달되지 않는다. 이름을 함께 읽힌다.
                                aria-label={`${dateValue}${holidayLabel ? `, ${holidayLabel}` : ''}${dateItemCount > 0 ? `, 할 일 ${dateItemCount}개` : ''}`}
                                title={holidayLabel || undefined}
                                onClick={() => selectDate(dateValue)}
                                key={dateValue}
                            >
                                {date.getDate()}
                                {dateItemCount > 0 && <span className={styles.dayMark} aria-hidden="true" />}
                            </button>
                        )
                    })}
                </div>
            </div>

            <div className={styles.todoPanel}>
                <header className={styles.todoHeader}>
                    <div>
                        <h1 id="daily-planner-title" className="sr-only">
                            {selectedDateFormatter.format(parseLocalDate(selectedDate))}
                        </h1>
                        {selectedHolidayNames.length > 0 && (
                            <p className={styles.holidayNames}>{selectedHolidayNames.join(', ')}</p>
                        )}
                    </div>
                </header>
                {message && <p className={styles.message} role="alert">{message}</p>}

                {isLoading ? (
                    <div className={styles.state} role="status"><IconLoader2 className={styles.spinner} size={19} />캘린더을 불러오는 중…</div>
                ) : status === 'error' ? (
                    <div className={styles.state}><p>캘린더을 불러오지 못했습니다.</p><button type="button" onClick={() => void loadMonth(visibleMonthKey, fromDate, toDate).catch(() => {})}>다시 불러오기</button></div>
                ) : (
                    <>
                        <ol className={styles.todoList}>
                            {items.map((item) => (
                                <li key={item.taskId} className={styles[`is-${item.status.toLowerCase()}`]}>
                                    <ChecklistCard
                                        status={item.status}
                                        title={
                                            <InlineEditableText
                                                wrap
                                                showEditButton
                                                displayClassName={styles['title-row']}
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
                                                disabled={pendingTaskId === item.taskId}
                                                session={selectedDate === today ? {
                                                    onStart: () => setSessionTaskId(item.taskId),
                                                    dialogId: 'create-session-dialog',
                                                    isOpen: sessionTaskId === item.taskId,
                                                } : undefined}
                                                onMove={() => setMoveTarget(item)}
                                                onDelete={() => void removeItem(item.taskId)}
                                            />
                                        </TaskMenu>
                                          </>
                                        )}
                                    />
                                </li>
                            ))}
                        </ol>
                        {items.length === 0 && <p className={styles.empty}>이 날짜에는 추가된 할 일이 없습니다.</p>}
                    </>
                )}

            </div>

            {isPickerOpen && <TaskPickerModal canMoveFromOtherDates planDateRequired initialPlanDate={selectedDate} onAddTasks={addPickedTasks} onClose={onPickerClose} />}
            {moveTarget && (
                <TaskInfoModal
                    taskId={moveTarget.taskId}
                    taskTitle={moveTarget.title}
                    currentFolderId={moveTarget.folderId}
                    currentPriority={moveTarget.priority}
                    currentUrgent={moveTarget.urgent}
                    currentPlanDate={selectedDate}
                    onClose={() => setMoveTarget(null)}
                />
            )}
            {sessionTaskId !== null && <CreateSessionModal todayTasks={todayTasks} initialTaskId={sessionTaskId} onClose={() => setSessionTaskId(null)} onStarted={(session) => { setSessionTaskId(null); navigate(`/sessions/${session.id}`) }} />}
        </section>
    )
}
