import { client } from '../../api/client'
import type { DailyPlan, UpdateDailyPlanRequest } from './dailyPlanTypes'

export async function getDailyPlans(fromDate: string, toDate: string): Promise<DailyPlan[]> {
  const response = await client.get<DailyPlan[]>('/daily-plan', {
    params: { from_date: fromDate, to_date: toDate },
  })
  return response.data
}

export async function updateDailyPlan(request: UpdateDailyPlanRequest): Promise<void> {
  await client.put('/daily-plan', request)
}
