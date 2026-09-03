import type { NoteContextType } from './noteTypes'

export interface TaskOrganizeRequest {
  memo: string
  // 참조할 폴더 범위. 생략하면 서버가 DEFAULT 로 보고 사용자의 모든 폴더를 참조한다.
  contextType?: NoteContextType
  // FOLDER 면 folderId, SESSION 이면 sessionId
  contextId?: number
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
