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

// 생성 응답은 조회 응답과 같은 모양이다. 만든 직후 다시 조회하지 않기 위해서다.
export type NoteCreateResponse = NoteResponse

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
