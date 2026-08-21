import type { TaskStatus } from '../tasks/taskTypes'

export interface DailyPlanItem {
  taskId: number
  projectId: number
  projectName: string
  title: string
  status: TaskStatus
  completionPct: number
  orderIdx: number
}

export interface DailyPlan {
  date: string
  items: DailyPlanItem[]
}

export interface UpdateDailyPlanRequest {
  date: string
  taskIds: number[]
}
