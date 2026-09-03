import type { TaskStatus } from './taskTypes'

/** 할 일 목록을 좁히는 조건. 상태·중요·즉시는 모두 AND로 걸린다. */
export interface TaskFilter {
  status: 'ALL' | TaskStatus
  priority: boolean
  urgent: boolean
}

export const EMPTY_TASK_FILTER: TaskFilter = { status: 'ALL', priority: false, urgent: false }

export function countActiveFilters(filter: TaskFilter) {
  return (filter.status === 'ALL' ? 0 : 1)
    + (filter.priority ? 1 : 0)
    + (filter.urgent ? 1 : 0)
}

export function matchesTaskFilter(
  task: { status: TaskStatus; priority: boolean; urgent: boolean },
  filter: TaskFilter,
) {
  return (filter.status === 'ALL' || task.status === filter.status)
    && (!filter.priority || task.priority)
    && (!filter.urgent || task.urgent)
}
