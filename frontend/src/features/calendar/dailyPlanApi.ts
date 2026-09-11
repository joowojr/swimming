import { client } from '../../api/client'
import type { CreateDailyPlanItemsRequest, DailyPlan } from './dailyPlanTypes'

export async function getDailyPlans(fromDate: string, toDate: string): Promise<DailyPlan[]> {
  const response = await client.get<DailyPlan[]>('/daily-plans', {
    params: { from_date: fromDate, to_date: toDate },
  })
  return response.data
}

export async function addDailyPlanItems(date: string, request: CreateDailyPlanItemsRequest): Promise<DailyPlan> {
  const response = await client.post<DailyPlan>(`/daily-plans/${date}/items`, request)
  return response.data
}

export async function deleteDailyPlanItem(date: string, itemId: number): Promise<void> {
  await client.delete(`/daily-plans/${date}/items/${itemId}`)
}
