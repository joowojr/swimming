import type { SessionDetailPlace, SessionPlace } from '../places/placeTypes'

export type SessionType = 'PERSONAL' | 'GROUP'

export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'INTERRUPTED'

export interface StartPersonalSessionRequest {
  taskIds: number[]
  placeId: number
  plannedDurationSec: number
  focusDurationSec: number
  breakDurationSec: number
  repeatCount: number
}

export interface SessionResponse {
  id: number
  type: SessionType
  taskIds: number[]
  place: SessionPlace
  musicUrl: string | null
  plannedDurationSec: number
  focusDurationSec: number
  breakDurationSec: number
  repeatCount: number
  actualDurationSec: number | null
  startedAt: string
  endedAt: string | null
  status: SessionStatus
}

export interface SessionTask {
  id: number
  folderId: number | null
  folderName: string | null
  title: string
}

export interface SessionDetailResponse {
  id: number
  type: SessionType
  status: SessionStatus
  plannedDurationSec: number
  focusDurationSec: number
  breakDurationSec: number
  repeatCount: number
  actualDurationSec: number | null
  startedAt: string
  endedAt: string | null
  place: SessionDetailPlace
  musicUrl: string | null
  tasks: SessionTask[]
}

export interface EndSessionTaskResult {
  taskId: number
  isCompleted: boolean
}

export interface EndSessionRequest {
  summary: string | null
  usePlannedDuration: boolean
  taskResults: EndSessionTaskResult[]
}

export interface UpdateSessionMusicUrlRequest {
  musicUrl: string | null
}

export interface UpdateSessionFocusDurationRequest {
  focusDurationSec: number
}
