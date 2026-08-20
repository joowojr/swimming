import type { TaskStatus } from './taskTypes'

export const TASK_STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: '시작 전',
  DOING: '하는 중',
  DONE: '완료',
  HOLD: '잠시 멈춤',
}

export const TASK_STATUS_VALUES = Object.keys(TASK_STATUS_LABEL) as TaskStatus[]
