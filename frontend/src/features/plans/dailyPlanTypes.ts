import type { TaskStatus } from '../tasks/taskTypes'

export interface DailyPlanItem {
  id: number
  taskId: number | null
  projectId: number | null
  projectName: string | null
  title: string
  status: TaskStatus | null
  orderIdx: number
}

export interface DailyPlan {
  date: string
  items: DailyPlanItem[]
}

export type CreateDailyPlanItemsRequest =
  | { taskIds: number[]; projectId?: never; title?: never }
  | { taskIds?: never; projectId?: number; title: string }

export interface ReorderDailyPlanItemsRequest {
  itemIds: number[]
}
