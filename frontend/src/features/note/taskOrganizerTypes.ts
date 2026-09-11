import type { TaskStatus } from '../tasks/taskTypes'
import type { NoteContextType } from './noteTypes'

export interface TaskOrganizeRequest {
  noteId: number
  memo: string
  // 상대 날짜 표현을 해석할 때 사용하는 브라우저 로컬 기준일
  currentDate: string
  // 참조할 폴더 범위. 생략하면 서버가 DEFAULT 로 보고 사용자의 모든 폴더를 참조한다.
  contextType?: NoteContextType
  // FOLDER 면 folderId, SESSION 이면 sessionId
  contextId?: number
}

export interface TaskSuggestionResponse {
  itemId: string
  sourceText: string
  folderId: number
  folderName: string
  title: string
  planDate: string | null
}

export interface UnclassifiedTaskResponse {
  itemId: string
  sourceText: string
  title: string
  planDate: string | null
}

export interface TaskOrganizeResponse {
  runId: number
  suggestions: TaskSuggestionResponse[]
  unclassified: UnclassifiedTaskResponse[]
}

export interface ApprovedTaskRequest {
  itemId: string
  sourceText: string
  folderId: number | null
  title: string
}

export interface TaskOrganizeConfirmRequest {
  runId: number
  noteId: number
  tasks: ApprovedTaskRequest[]
}

export interface CreatedTaskResponse {
  id: number
  folderId: number | null
  title: string
  status: TaskStatus
  priority: boolean
  urgent: boolean
}

export interface TaskOrganizeConfirmResponse {
  createdTasks: CreatedTaskResponse[]
}
