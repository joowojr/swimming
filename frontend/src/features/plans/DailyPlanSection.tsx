import { useEffect, useMemo, useState } from 'react'
import { IconArrowDown, IconArrowUp, IconCalendar, IconCheck, IconDots, IconLoader2, IconPlus, IconTrash } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import type { Project, ProjectDetail } from '../projects/projectTypes'
import { TASK_STATUS_LABEL } from '../tasks/taskLabels'
import { getDailyPlans, updateDailyPlan } from './dailyPlanApi'
import type { DailyPlan, DailyPlanItem } from './dailyPlanTypes'
import TaskPickerModal from './TaskPickerModal'
import styles from './DailyPlanSection.module.css'

interface DailyPlanSectionProps { projects: Project[] }

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

const dayFormatter = new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' })
const weekdayFormatter = new Intl.DateTimeFormat('ko-KR', { weekday: 'short' })
const rangeFormatter = new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' })

export default function DailyPlanSection({ projects }: DailyPlanSectionProps) {
  const today = useMemo(() => formatLocalDate(new Date()), [])
  const [fromDate, setFromDate] = useState(today)
  const [toDate, setToDate] = useState(addDays(today, 6))
  const [selectedDate, setSelectedDate] = useState(today)
  const [plans, setPlans] = useState<DailyPlan[]>([])
  const [drafts, setDrafts] = useState<Record<string, DailyPlanItem[]>>({})
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [dirtyDates, setDirtyDates] = useState<Set<string>>(new Set())
  const [isSaving, setIsSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [isPickerOpen, setIsPickerOpen] = useState(false)

  useEffect(() => {
    let active = true
    void getDailyPlans(fromDate, toDate)
      .then((response) => {
        if (!active) return
        setPlans(response)
        setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
        setDirtyDates(new Set())
        setStatus('ready')
      })
      .catch(() => { if (active) setStatus('error') })
    return () => { active = false }
  }, [fromDate, toDate])

  const draftItems = drafts[selectedDate] ?? []
  const isDirty = dirtyDates.has(selectedDate)

  const retry = async () => {
    setStatus('loading')
    try {
      const response = await getDailyPlans(fromDate, toDate)
      setPlans(response)
      setDrafts(Object.fromEntries(response.map((plan) => [plan.date, plan.items])))
      setDirtyDates(new Set())
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

  const moveItem = (index: number, offset: number) => {
    const target = index + offset
    if (target < 0 || target >= draftItems.length) return
    setDrafts((current) => {
      const items = current[selectedDate] ?? []
      const next = [...items]
      ;[next[index], next[target]] = [next[target], next[index]]
      return { ...current, [selectedDate]: next }
    })
    setDirtyDates((dates) => new Set(dates).add(selectedDate))
    setMessage(null)
  }

  const addTasks = (tasks: ProjectDetail['tasks'], project: Project) => {
    setDrafts((current) => {
      const items = current[selectedDate] ?? []
      return {
        ...current,
        [selectedDate]: [
          ...items,
          ...tasks
            .filter((task) => !items.some((item) => item.taskId === task.id))
            .map((task, index) => ({
              taskId: task.id,
              projectId: project.id,
              projectName: project.name,
              title: task.title,
              status: task.status,
              completionPct: task.completionPct,
              orderIdx: items.length + index,
            })),
        ],
      }
    })
    setDirtyDates((dates) => new Set(dates).add(selectedDate))
    setMessage(null)
  }

  const save = async () => {
    setIsSaving(true)
    setMessage(null)
    try {
      await updateDailyPlan({ date: selectedDate, taskIds: draftItems.map((item) => item.taskId) })
      setPlans((current) => current.map((plan) => plan.date === selectedDate
        ? { ...plan, items: draftItems.map((item, orderIdx) => ({ ...item, orderIdx })) }
        : plan))
      setDirtyDates((dates) => {
        const next = new Set(dates)
        next.delete(selectedDate)
        return next
      })
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null ? (error as ApiError).message : undefined
      setMessage(apiMessage ?? '계획을 저장하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <section className={styles.section} aria-labelledby="daily-plan-title">
      <header className={styles.heading}>
        <div className={styles['title-group']}>
          <div>
            <h2 id="daily-plan-title">오늘의 계획</h2>
            <p>날짜별로 이어갈 Task를 정리합니다.</p>
          </div>
        </div>
        <div className={styles['primary-actions']}>
          <button type="button" className={styles['save-button']} disabled={!isDirty || isSaving} onClick={() => void save()}>
            {isSaving ? <IconLoader2 className={styles.spinner} size={16} aria-hidden="true" /> : <IconCheck size={16} aria-hidden="true" />}
            {isSaving ? '저장 중…' : '계획 저장'}
          </button>
        </div>
      </header>

      <div className={styles['database-toolbar']}>
        <details className={styles.range}>
          <summary aria-label="계획 조회 기간 변경">
            <IconCalendar size={16} aria-hidden="true" />
            {rangeFormatter.format(new Date(`${fromDate}T00:00:00`))}–{rangeFormatter.format(new Date(`${toDate}T00:00:00`))}
          </summary>
          <div className={styles['range-popover']} aria-label="계획 조회 기간">
            <label><span>시작일</span><input type="date" value={fromDate} onChange={(event) => changeFromDate(event.target.value)} /></label>
            <label><span>종료일</span><input type="date" value={toDate} min={fromDate} max={addDays(fromDate, 6)} onChange={(event) => changeToDate(event.target.value)} /></label>
            <p>시작일을 포함해 최대 7일까지 볼 수 있습니다.</p>
          </div>
        </details>
        {message && <p className={styles['save-status']} role="alert">{message}</p>}
      </div>

      {status === 'loading' ? (
        <div className={styles.state} role="status"><IconLoader2 className={styles.spinner} size={19} />계획을 불러오는 중…</div>
      ) : status === 'error' ? (
        <div className={styles.state}><p>계획을 불러오지 못했습니다.</p><button type="button" onClick={() => void retry()}>다시 불러오기</button></div>
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
                      onClick={() => { setSelectedDate(plan.date); setMessage(null) }}
                    >
                      <span className={styles['date-name']}><strong>{weekdayFormatter.format(date)}</strong>{dayFormatter.format(date)}</span>
                      <span className={styles.count}>{items.length}</span>
                      {dirtyDates.has(plan.date) && <span className={styles['dirty-dot']}><span className="sr-only">저장하지 않은 변경 있음</span></span>}
                    </button>
                    <ol className={styles.list}>
                      {items.map((item, index) => (
                        <li className={`${styles.card} ${styles[`project-tone-${item.projectId % 3}`]}`} key={item.taskId}>
                          <button type="button" className={styles['card-select']} onClick={() => setSelectedDate(plan.date)} aria-label={`${item.title}이 있는 ${dayFormatter.format(date)} 선택`}>
                            <span className={styles['project-label']}><span className={styles['project-mark']} aria-hidden="true" />{item.projectName}</span>
                            <strong>{item.title}</strong>
                            <span className={styles.meta}><span>{TASK_STATUS_LABEL[item.status]}</span><span>{item.completionPct}% 진행</span></span>
                          </button>
                          {selected && (
                            <details className={styles['card-menu']}>
                              <summary aria-label={`${item.title} 카드 메뉴`}><IconDots size={17} aria-hidden="true" /></summary>
                              <div className={styles.actions}>
                                <button type="button" disabled={index === 0} onClick={() => moveItem(index, -1)}><IconArrowUp size={15} aria-hidden="true" />위로</button>
                                <button type="button" disabled={index === items.length - 1} onClick={() => moveItem(index, 1)}><IconArrowDown size={15} aria-hidden="true" />아래로</button>
                                <button type="button" onClick={() => { setDrafts((current) => ({ ...current, [selectedDate]: (current[selectedDate] ?? []).filter((task) => task.taskId !== item.taskId) })); setDirtyDates((dates) => new Set(dates).add(selectedDate)); setMessage(null) }}><IconTrash size={15} aria-hidden="true" /></button>
                              </div>
                            </details>
                          )}
                        </li>
                      ))}
                    </ol>
                    {items.length === 0 && <p className={styles.empty}>아직 계획된 Task가 없습니다.</p>}
                    <button type="button" className={styles['column-add']} onClick={() => { setSelectedDate(plan.date); setIsPickerOpen(true) }}><IconPlus size={15} aria-hidden="true" /></button>
                  </section>
                )
              })}
            </div>
          </div>
        </>
      )}

      {isPickerOpen && <TaskPickerModal projects={projects} selectedTaskIds={new Set(draftItems.map((item) => item.taskId))} onAdd={addTasks} onClose={() => setIsPickerOpen(false)} />}
    </section>
  )
}
