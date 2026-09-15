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

/** 캘린더에 새로 만들어 담을 할 일 하나. folderId를 빼면 미분류다. */
export interface NewDailyPlanTask {
  title: string
  folderId?: number
  priority?: boolean
  urgent?: boolean
}

/**
 * 이미 있는 할 일을 담거나(taskIds), 새 할 일을 만들어 담는다(tasks).
 * 둘 다 모달 하나의 "모두 추가" 한 번이라 서버가 한 트랜잭션으로 처리한다.
 */
export type CreateDailyPlanItemsRequest =
  | { taskIds: number[]; tasks?: never }
  | { taskIds?: never; tasks: NewDailyPlanTask[] }

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
