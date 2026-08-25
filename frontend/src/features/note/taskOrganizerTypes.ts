export interface TaskOrganizeRequest {
  memo: string
}

export interface TaskSuggestionResponse {
  sourceText: string
  projectId: number
  projectName: string
  title: string
}

export interface UnclassifiedTaskResponse {
  sourceText: string
  title: string
}

export interface TaskOrganizeResponse {
  suggestions: TaskSuggestionResponse[]
  unclassified: UnclassifiedTaskResponse[]
}

export interface ApprovedTaskRequest {
  sourceText: string
  projectId: number | null
  title: string
}

export interface TaskOrganizeConfirmRequest {
  noteId: number
  tasks: ApprovedTaskRequest[]
}

export interface CreatedTaskResponse {
  id: number
  projectId: number | null
  title: string
}

export interface TaskOrganizeConfirmResponse {
  createdTasks: CreatedTaskResponse[]
}
