import type { Folder } from '../folders/folderTypes.ts'
import type { TaskCacheEntry } from '../tasks/taskTypes'
import type { DailyPlanItem, PlanEntry } from './dailyPlanTypes'

/**
 * 계획 항목(멤버십) + task(가변 속성) + 폴더(이름)를 화면이 쓰는 한 줄로 합친다.
 * task가 아직 캐시에 없으면 그릴 수 없으므로 건너뛴다.
 */
export function joinPlanItems(
  entries: PlanEntry[],
  tasksById: Record<number, TaskCacheEntry>,
  folders: Folder[],
): DailyPlanItem[] {
  return entries.flatMap((entry): DailyPlanItem[] => {
    const task = tasksById[entry.taskId]
    if (!task) return []

    const base = {
      id: entry.id,
      taskId: entry.taskId,
      title: task.title,
      status: task.status,
      priority: task.priority,
      urgent: task.urgent,
    }

    // itemType과 폴더 이름은 서버 값이 아니라 task.folderId에서 파생한다.
    // 폴더를 옮기면 계획 화면에도 곧바로 반영돼야 하기 때문이다. entry의 값은 폴백으로만 쓴다.
    if (task.folderId !== null) {
      const folderName = folders.find((folder) => folder.id === task.folderId)?.name
      return [{ ...base, itemType: 'TASK' as const, folderId: task.folderId, folderName: folderName ?? entry.folderName ?? '폴더' }]
    }
    return [{ ...base, itemType: 'AD_HOC' as const, folderId: null, folderName: null }]
  })
}

export function toPlanEntries(items: DailyPlanItem[]): PlanEntry[] {
  return items.map((item) => ({
    id: item.id,
    taskId: item.taskId,
    itemType: item.itemType,
    folderName: item.folderName,
  }))
}

/** 계획 응답에 실려 온 task 정보를 taskStore가 받을 모양으로 바꾼다. */
export function toTaskEntries(items: DailyPlanItem[]): TaskCacheEntry[] {
  return items.map((item) => ({
    id: item.taskId,
    folderId: item.folderId,
    title: item.title,
    status: item.status,
    priority: item.priority,
    urgent: item.urgent,
  }))
}
