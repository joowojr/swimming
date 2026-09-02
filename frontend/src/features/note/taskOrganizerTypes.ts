export interface TaskOrganizeRequest {
  memo: string
}

export interface TaskSuggestionResponse {
  sourceText: string
  folderId: number
  folderName: string
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
  folderId: number | null
  title: string
}

export interface TaskOrganizeConfirmRequest {
  noteId: number
  tasks: ApprovedTaskRequest[]
}

export interface CreatedTaskResponse {
  id: number
  folderId: number | null
  title: string
}

export interface TaskOrganizeConfirmResponse {
  createdTasks: CreatedTaskResponse[]
}
