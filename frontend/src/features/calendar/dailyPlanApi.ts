import { client } from '../../api/client'
import type { CreateDailyPlanItemsRequest, DailyPlan } from './dailyPlanTypes'

export async function getDailyPlans(fromDate: string, toDate: string): Promise<DailyPlan[]> {
  const response = await client.get<DailyPlan[]>('/daily-plans', {
    params: { from_date: fromDate, to_date: toDate },
  })
  return response.data
}

/** 그 날짜에 할 일을 담는다. 다른 날짜에 담겨 있던 할 일은 이 날짜로 옮겨진다. */
export async function addDailyPlanTasks(date: string, request: CreateDailyPlanItemsRequest): Promise<DailyPlan> {
  const response = await client.post<DailyPlan>(`/daily-plans/${date}/tasks`, request)
  return response.data
}

/** 그 날짜의 캘린더에서 뺀다. 할 일 자체는 남는다. */
export async function removeDailyPlanTask(date: string, taskId: number): Promise<void> {
  await client.delete(`/daily-plans/${date}/tasks/${taskId}`)
}
