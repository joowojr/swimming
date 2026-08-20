export type ProjectStatus = 'IN_PROGRESS' | 'ARCHIVED'

export interface ProjectTag {
  id: number
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
