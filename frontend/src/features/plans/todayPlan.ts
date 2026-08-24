import { addDailyPlanItem, getDailyPlans } from './dailyPlanApi'
import type { DailyPlanItem } from './dailyPlanTypes'

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export async function getTodayPlanItems(): Promise<DailyPlanItem[]> {
  const date = formatLocalDate(new Date())
  const plans = await getDailyPlans(date, date)
  return plans.find((plan) => plan.date === date)?.items ?? []
}

// 세션은 오늘 계획에 담긴 Task로만 시작할 수 있어, 계획에 없으면 먼저 담는다.
export async function ensureTodayPlanItem(taskId: number): Promise<DailyPlanItem[]> {
  const items = await getTodayPlanItems()
  if (items.some((item) => item.taskId === taskId)) return items

  const plan = await addDailyPlanItem(formatLocalDate(new Date()), { taskId })
  return plan.items
}
