import { useDailyPlanStore } from '../../store/dailyPlanStore'
import { useFolderStore } from '../../store/folderStore.ts'
import { useTaskStore } from '../../store/taskStore'
import type { DailyPlanItem } from './dailyPlanTypes'
import { formatLocalDate, parseLocalDate } from '../../lib/date'
import { monthKeyOf, monthRange } from './planDate'
import { joinPlanItems } from './planItems'

// 세션은 오늘 캘린더에 담긴 Task로만 시작할 수 있어, 캘린더에 없으면 먼저 담는다.
// store를 거치므로 여기서 담은 항목이 데일리 플래너에도 곧바로 나타난다.
export async function ensureTodayPlanItem(taskId: number): Promise<DailyPlanItem[]> {
  const date = formatLocalDate(new Date())
  await useDailyPlanStore.getState().ensureItem(date, taskId)

  return joinPlanItems(
    useDailyPlanStore.getState().entriesByDate[date] ?? [],
    useTaskStore.getState().byId,
    useFolderStore.getState().folders,
  )
}

// 오늘 캘린더을 store 기준으로 돌려준다. 아직 이번 달을 받지 않았으면 받아 온다.
export async function getTodayPlanItems(): Promise<DailyPlanItem[]> {
  const date = formatLocalDate(new Date())
  const store = useDailyPlanStore.getState()
  const monthKey = monthKeyOf(date)

  if (!store.loadedMonths.has(monthKey)) {
    const { fromDate, toDate } = monthRange(parseLocalDate(date))
    await store.loadMonth(monthKey, fromDate, toDate)
  }

  return joinPlanItems(
    useDailyPlanStore.getState().entriesByDate[date] ?? [],
    useTaskStore.getState().byId,
    useFolderStore.getState().folders,
  )
}
