import type { TaskSummaryResponse } from '../tasks/taskTypes'

export type ProjectStatus = 'IN_PROGRESS' | 'ARCHIVED'

export type ProjectLoadStatus = 'idle' | 'loading' | 'ready' | 'error'

export interface ProjectTag {
  id: number
  name: string
}

export interface ProjectTagNameRequest {
  name: string
}

export interface Project {
  id: number
  name: string
  description: string
  targetDate: string | null
  status: ProjectStatus
  tag: ProjectTag | null
  createdAt: string
  updatedAt: string
}

export interface ProjectProgress {
  totalTaskCount: number
  completedTaskCount: number
  completionPct: number
}

export interface ProjectDetail {
  id: number
  name: string
  description: string
  targetDate: string | null
  status: ProjectStatus
  tag: ProjectTag | null
  progress: ProjectProgress
  tasks: TaskSummaryResponse[]
}

export interface CreateProjectRequest {
  name: string
  description: string
  targetDate: string | null
  tagId: number | null
  newTagName: string | null
}

export interface UpdateProjectRequest {
  name: string
  description: string
  targetDate: string | null
  status: ProjectStatus
  tagId: number | null
}

export type ProjectFieldErrors = Partial<
  Record<keyof CreateProjectRequest | 'status', string>
>
