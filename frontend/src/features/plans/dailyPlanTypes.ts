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

/**
 * store에 담기는 캘린더 항목. 캘린더이 소유하는 것은 "어떤 날짜에 어떤 task가 어떤 순서로 있는가"뿐이고,
 * task의 가변 속성(title·status·priority·urgent)은 taskStore가 소유한다.
 * folderName은 folderStore에 폴더가 없을 때 쓰는 표시용 폴백이다.
 */
export interface PlanEntry {
  id: number
  taskId: number
  itemType: 'TASK' | 'AD_HOC'
  folderName: string | null
}
