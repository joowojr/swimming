export type NoteContextType = 'DEFAULT' | 'PROJECT' | 'SESSION'

export type NoteStatus = 'ACTIVE' | 'ARCHIVED'

export interface NoteResponse {
  id: number
  content: string
  status: NoteStatus
  contextType: NoteContextType
  projectId: number | null
  sessionId: number | null
  createdAt: string
  updatedAt: string
}

export interface NoteCreateResponse {
  id: number
}

export type CreateNoteRequest =
  | {
      content: string
      contextType: 'DEFAULT'
      projectId: null
      sessionId: null
    }
  | {
      content: string
      contextType: 'PROJECT'
      projectId: number
      sessionId: null
    }
  | {
      content: string
      contextType: 'SESSION'
      projectId: null
      sessionId: number
    }

export interface UpdateNoteRequest {
  content: string
}

export type GetNotesParams =
  | {
      status?: NoteStatus
      contextType?: never
      projectId?: never
      sessionId?: never
    }
  | {
      status?: NoteStatus
      contextType: NoteContextType
      projectId?: never
      sessionId?: never
    }
  | {
      status?: NoteStatus
      contextType?: never
      projectId: number
      sessionId?: never
    }
  | {
      status?: NoteStatus
      contextType?: never
      projectId?: never
      sessionId: number
    }
