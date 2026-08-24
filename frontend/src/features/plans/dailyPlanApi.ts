import { client } from '../../api/client'
import type { CreateDailyPlanItemsRequest, DailyPlan, ReorderDailyPlanItemsRequest } from './dailyPlanTypes'

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

export async function reorderDailyPlanItems(date: string, request: ReorderDailyPlanItemsRequest): Promise<DailyPlan> {
  const response = await client.put<DailyPlan>(`/daily-plans/${date}`, request)
  return response.data
}

export async function updateDailyPlanItem(date: string, itemId: number, title: string): Promise<DailyPlan> {
  const response = await client.patch<DailyPlan>(`/daily-plans/${date}/items/${itemId}`, { title })
  return response.data
}

export async function deleteDailyPlanItem(date: string, itemId: number): Promise<void> {
  await client.delete(`/daily-plans/${date}/items/${itemId}`)
}
