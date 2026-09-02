export type NoteContextType = 'DEFAULT' | 'PROJECT' | 'SESSION'

export type NoteStatus = 'ACTIVE' | 'ARCHIVED'

export interface NoteResponse {
  id: number
  content: string
  status: NoteStatus
  contextType: NoteContextType
  folderId: number | null
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
      folderId: null
      sessionId: null
    }
  | {
      content: string
      contextType: 'PROJECT'
      folderId: number
      sessionId: null
    }
  | {
      content: string
      contextType: 'SESSION'
      folderId: null
      sessionId: number
    }

export interface UpdateNoteRequest {
  content: string
}

export type GetNotesParams =
  | {
      status?: NoteStatus
      contextType?: never
      folderId?: never
      sessionId?: never
    }
  | {
      status?: NoteStatus
      contextType: NoteContextType
      folderId?: never
      sessionId?: never
    }
  | {
      status?: NoteStatus
      contextType?: never
      folderId: number
      sessionId?: never
    }
  | {
      status?: NoteStatus
      contextType?: never
      folderId?: never
      sessionId: number
    }
