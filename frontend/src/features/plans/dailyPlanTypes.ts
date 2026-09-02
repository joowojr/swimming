import type { TaskStatus } from '../tasks/taskTypes'

interface DailyPlanItemBase {
  id: number
  taskId: number
  title: string
  status: TaskStatus
  priority: boolean
  urgent: boolean
}

export type DailyPlanItem = DailyPlanItemBase & (
  | { itemType: 'TASK'; projectId: number; projectName: string }
  | { itemType: 'AD_HOC'; projectId: null; projectName: null }
)

export interface DailyPlan {
  date: string
  items: DailyPlanItem[]
}

export type CreateDailyPlanItemsRequest =
  | { taskIds: number[]; projectId?: never; title?: never; priority?: never; urgent?: never }
  | { taskIds?: never; projectId?: number; title: string; priority?: boolean; urgent?: boolean }
