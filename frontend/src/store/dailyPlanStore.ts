import { create } from 'zustand'
import { addDailyPlanTasks, getDailyPlans, removeDailyPlanTask } from '../features/calendar/dailyPlanApi'
import { parseLocalDate } from '../lib/date'
import { monthKeyOf, monthRange } from '../features/calendar/planDate'
import { toPlanEntries, toTaskEntries } from '../features/calendar/planItems'
import type { CreateDailyPlanItemsRequest, PlanEntry } from '../features/calendar/dailyPlanTypes'
import { useTaskStore } from './taskStore'

/** 한 할 일은 날짜 하나에만 담기므로, 다른 날짜로 옮겨 간 할 일을 나머지 날짜에서 뺀다. */
function withoutTasks(
  entriesByDate: Record<string, PlanEntry[]>,
  taskIds: Set<number>,
  exceptDate?: string,
): Record<string, PlanEntry[]> {
  return Object.fromEntries(Object.entries(entriesByDate).map(([date, entries]) => [
    date,
    date === exceptDate ? entries : entries.filter((entry) => !taskIds.has(entry.taskId)),
  ]))
}

/**
 * 역할: 날짜별 캘린더 항목의 단일 출처. 어느 화면에서 캘린더에 담아도 캘린더을 보는 화면이 같은 목록을 본다.
 * task의 가변 속성은 담지 않고 taskStore가 소유한다. 캘린더 응답에 실려 온 task는 taskStore에 넣어 둔다.
 * 실패는 던지고, 사용자에게 보일 문구는 화면이 정한다.
 */
type DailyPlanStatus = 'idle' | 'loading' | 'ready' | 'error'

interface DailyPlanStoreState {
  entriesByDate: Record<string, PlanEntry[]>
  // 이미 받아둔 달. 캘린더에 담는 것만으로는 채워지지 않는다(그 달을 다 받은 것이 아니므로).
  loadedMonths: Set<string>
  status: DailyPlanStatus

  loadMonth: (monthKey: string, fromDate: string, toDate: string) => Promise<void>
  addItems: (date: string, request: CreateDailyPlanItemsRequest) => Promise<void>
  removeTask: (date: string, taskId: number) => Promise<void>
  applyTaskDate: (taskId: number, planDate: string | null) => void
  ensureItem: (date: string, taskId: number) => Promise<void>
  invalidateDate: (date: string) => void
  reset: () => void
}

// 달을 빠르게 오갈 때 늦게 도착한 응답이 최신 달을 덮어쓰지 않게 한다.
let latestRequestId = 0

export const useDailyPlanStore = create<DailyPlanStoreState>((set, get) => ({
  entriesByDate: {},
  loadedMonths: new Set(),
  status: 'idle',

  loadMonth: async (monthKey, fromDate, toDate) => {
    const requestId = ++latestRequestId
    set({ status: 'loading' })

    try {
      const plans = await getDailyPlans(fromDate, toDate)
      if (requestId !== latestRequestId) return

      useTaskStore.getState().upsert(plans.flatMap(toTaskEntries))
      set((current) => ({
        entriesByDate: {
          ...current.entriesByDate,
          ...Object.fromEntries(plans.map((plan) => [plan.date, toPlanEntries(plan.items)])),
        },
        loadedMonths: new Set(current.loadedMonths).add(monthKey),
        status: 'ready',
      }))
    } catch (error) {
      if (requestId !== latestRequestId) return
      set({ status: 'error' })
      throw error
    }
  },

  // 다른 날짜에 담겨 있던 할 일은 이 날짜로 옮겨지므로 원래 날짜에서도 뺀다.
  addItems: async (date, request) => {
    const plan = await addDailyPlanTasks(date, request)

    useTaskStore.getState().upsert(toTaskEntries(plan))
    const plannedIds = new Set(plan.items.map((item) => item.taskId))
    set((current) => ({
      entriesByDate: {
        ...withoutTasks(current.entriesByDate, plannedIds, plan.date),
        [plan.date]: toPlanEntries(plan.items),
      },
      status: 'ready',
    }))
  },

  removeTask: async (date, taskId) => {
    await removeDailyPlanTask(date, taskId)

    const task = useTaskStore.getState().byId[taskId]
    if (task) useTaskStore.getState().upsert([{ ...task, planDate: null }])
    set((current) => ({
      entriesByDate: {
        ...current.entriesByDate,
        [date]: (current.entriesByDate[date] ?? []).filter((entry) => entry.taskId !== taskId),
      },
    }))
  },

  // 캘린더 밖에서 할 일의 날짜를 바꿨을 때(수정 모달) 반영한다. 원래 날짜에서는 바로 빼고,
  // 새 날짜는 하루 안의 순서를 서버가 정하므로 그 달을 다시 받게 한다.
  applyTaskDate: (taskId, planDate) => set((current) => {
    const alreadyThere = planDate !== null
      && (current.entriesByDate[planDate] ?? []).some((entry) => entry.taskId === taskId)
    if (alreadyThere) return {}

    const loadedMonths = new Set(current.loadedMonths)
    if (planDate !== null) loadedMonths.delete(monthKeyOf(planDate))
    return {
      entriesByDate: withoutTasks(current.entriesByDate, new Set([taskId])),
      loadedMonths,
    }
  }),

  // 세션은 캘린더에 담긴 Task로만 시작할 수 있어, 캘린더에 없으면 먼저 담는다.
  ensureItem: async (date, taskId) => {
    const monthKey = monthKeyOf(date)
    if (!get().loadedMonths.has(monthKey)) {
      const { fromDate, toDate } = monthRange(parseLocalDate(date))
      await get().loadMonth(monthKey, fromDate, toDate)
    }
    if (get().entriesByDate[date]?.some((entry) => entry.taskId === taskId)) return

    await get().addItems(date, { taskIds: [taskId] })
  },

  // 캘린더 항목을 돌려주지 않는 변경(할 일을 만들며 캘린더에 담기)은 로컬 패치가 안 된다.
  // 그 달을 받은 적 없는 상태로 되돌려 캘린더 화면이 다시 받게 한다.
  invalidateDate: (date) => set((current) => {
    const loadedMonths = new Set(current.loadedMonths)
    loadedMonths.delete(monthKeyOf(date))
    return { loadedMonths }
  }),

  reset: () => {
    latestRequestId += 1
    set({ entriesByDate: {}, loadedMonths: new Set(), status: 'idle' })
  },
}))
