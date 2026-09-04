import type { TaskStatus } from './taskTypes'

/** 할 일 목록을 좁히는 조건. 상태·중요·즉시·미분류는 모두 AND로 걸린다. */
export interface TaskFilter {
  status: 'ALL' | TaskStatus
  priority: boolean
  urgent: boolean
  /** 폴더 없는 할 일만 본다. 폴더 페이지의 '전체 / 미분류' 구분을 대신한다. */
  unclassifiedOnly: boolean
}

export const EMPTY_TASK_FILTER: TaskFilter = {
  status: 'ALL',
  priority: false,
  urgent: false,
  unclassifiedOnly: false,
}

export function countActiveFilters(filter: TaskFilter) {
  return (filter.status === 'ALL' ? 0 : 1)
    + (filter.priority ? 1 : 0)
    + (filter.urgent ? 1 : 0)
    + (filter.unclassifiedOnly ? 1 : 0)
}

export function matchesTaskFilter(
  task: { status: TaskStatus; priority: boolean; urgent: boolean; folderId?: number | null },
  filter: TaskFilter,
) {
  return (filter.status === 'ALL' || task.status === filter.status)
    && (!filter.priority || task.priority)
    && (!filter.urgent || task.urgent)
    && (!filter.unclassifiedOnly || (task.folderId ?? null) === null)
}
