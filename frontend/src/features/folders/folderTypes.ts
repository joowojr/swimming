import type { CursorPage } from '../../api/types'
import type { TaskSummaryResponse } from '../tasks/taskTypes'

export type FolderStatus = 'IN_PROGRESS' | 'ARCHIVED'

export type FolderLoadStatus = 'idle' | 'loading' | 'ready' | 'error'

export interface FolderTag {
  id: number
  name: string
}

export interface FolderTagNameRequest {
  name: string
}

export interface Folder {
  id: number
  name: string
  description: string
  targetDate: string | null
  status: FolderStatus
  tag: FolderTag | null
  createdAt: string
  updatedAt: string
}

export interface FolderProgress {
  totalTaskCount: number
  completedTaskCount: number
  completionPct: number
}

export interface FolderDetail {
  id: number
  name: string
  description: string
  targetDate: string | null
  status: FolderStatus
  tag: FolderTag | null
  progress: FolderProgress
  tasks: CursorPage<TaskSummaryResponse>
}

export interface CreateFolderRequest {
  name: string
  description: string
  targetDate: string | null
  tagId: number | null
  newTagName: string | null
}

export interface UpdateFolderRequest {
  name: string
  description: string
  targetDate: string | null
  status: FolderStatus
  tagId: number | null
}

export type FolderFieldErrors = Partial<
  Record<keyof CreateFolderRequest | 'status', string>
>
