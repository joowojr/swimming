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
  | { itemType: 'TASK'; folderId: number; folderName: string }
  | { itemType: 'AD_HOC'; folderId: null; folderName: null }
)

export interface DailyPlan {
  date: string
  items: DailyPlanItem[]
}

export type CreateDailyPlanItemsRequest =
  | { taskIds: number[]; folderId?: never; title?: never; priority?: never; urgent?: never }
  | { taskIds?: never; folderId?: number; title: string; priority?: boolean; urgent?: boolean }
