import { create } from 'zustand'
import { addDailyPlanItems, deleteDailyPlanItem, getDailyPlans } from '../features/plans/dailyPlanApi'
import { parseLocalDate } from '../lib/date'
import { monthKeyOf, monthRange } from '../features/plans/planDate'
import { toPlanEntries, toTaskEntries } from '../features/plans/planItems'
import type { CreateDailyPlanItemsRequest, DailyPlan, PlanEntry } from '../features/plans/dailyPlanTypes'
import { useTaskStore } from './taskStore'

/**
 * 역할: 날짜별 계획 항목의 단일 출처. 어느 화면에서 계획에 담아도 계획을 보는 화면이 같은 목록을 본다.
 * task의 가변 속성은 담지 않고 taskStore가 소유한다. 계획 응답에 실려 온 task는 taskStore에 넣어 둔다.
 * 실패는 던지고, 사용자에게 보일 문구는 화면이 정한다.
 */
type DailyPlanStatus = 'idle' | 'loading' | 'ready' | 'error'

interface DailyPlanStoreState {
  entriesByDate: Record<string, PlanEntry[]>
  // 이미 받아둔 달. 계획에 담는 것만으로는 채워지지 않는다(그 달을 다 받은 것이 아니므로).
  loadedMonths: Set<string>
  status: DailyPlanStatus

  loadMonth: (monthKey: string, fromDate: string, toDate: string) => Promise<void>
  addItems: (date: string, request: CreateDailyPlanItemsRequest) => Promise<void>
  removeItem: (date: string, itemId: number) => Promise<void>
  applyPlans: (plans: DailyPlan[]) => void
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

      useTaskStore.getState().upsert(plans.flatMap((plan) => toTaskEntries(plan.items)))
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

  addItems: async (date, request) => {
    const plan = await addDailyPlanItems(date, request)

    useTaskStore.getState().upsert(toTaskEntries(plan.items))
    set((current) => ({
      entriesByDate: { ...current.entriesByDate, [plan.date]: toPlanEntries(plan.items) },
      status: 'ready',
    }))
  },

  removeItem: async (date, itemId) => {
    await deleteDailyPlanItem(date, itemId)

    set((current) => ({
      entriesByDate: {
        ...current.entriesByDate,
        [date]: (current.entriesByDate[date] ?? []).filter((entry) => entry.id !== itemId),
      },
    }))
  },

  // 계획 밖에서 일어난 변경(할 일 이동)이 돌려준 날짜들을 그대로 반영한다.
  // 이동은 원본과 대상 두 날짜를 함께 바꾸므로 서버가 둘 다 실어 보낸다.
  // TODO(task-owns-plan-date): 계획 날짜가 task 컬럼이 되면 이동 응답에 plans가 없어지고
  //   task 하나만 반영하면 된다. docs/backlog/task-owns-plan-date.md
  applyPlans: (plans) => {
    if (plans.length === 0) return

    useTaskStore.getState().upsert(plans.flatMap((plan) => toTaskEntries(plan.items)))
    set((current) => ({
      entriesByDate: {
        ...current.entriesByDate,
        ...Object.fromEntries(plans.map((plan) => [plan.date, toPlanEntries(plan.items)])),
      },
      status: 'ready',
    }))
  },

  // 세션은 계획에 담긴 Task로만 시작할 수 있어, 계획에 없으면 먼저 담는다.
  ensureItem: async (date, taskId) => {
    const monthKey = monthKeyOf(date)
    if (!get().loadedMonths.has(monthKey)) {
      const { fromDate, toDate } = monthRange(parseLocalDate(date))
      await get().loadMonth(monthKey, fromDate, toDate)
    }
    if (get().entriesByDate[date]?.some((entry) => entry.taskId === taskId)) return

    await get().addItems(date, { taskIds: [taskId] })
  },

  // 계획 항목을 돌려주지 않는 변경(할 일을 만들며 계획에 담기)은 로컬 패치가 안 된다.
  // 그 달을 받은 적 없는 상태로 되돌려 계획 화면이 다시 받게 한다.
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
