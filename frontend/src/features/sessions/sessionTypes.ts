import type { SessionPlace } from '../places/placeTypes'

export type SessionType = 'PERSONAL' | 'GROUP'

export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'INTERRUPTED'

export interface StartPersonalSessionRequest {
  taskIds: number[]
  placeId: number
  plannedDurationSec: number
}

export interface SessionResponse {
  id: number
  type: SessionType
  taskIds: number[]
  place: SessionPlace
  musicUrl: string | null
  plannedDurationSec: number
  actualDurationSec: number | null
  startedAt: string
  endedAt: string | null
  status: SessionStatus
}

export interface ActiveSessionTask {
  id: number
  projectId: number
  projectName: string
  title: string
}

export interface ActiveSessionResponse {
  id: number
  type: SessionType
  status: 'IN_PROGRESS'
  plannedDurationSec: number
  startedAt: string
  place: SessionPlace
  musicUrl: string | null
  tasks: ActiveSessionTask[]
}

export interface SessionDetailResponse {
  id: number
  type: SessionType
  status: SessionStatus
  plannedDurationSec: number
  actualDurationSec: number | null
  startedAt: string
  endedAt: string | null
  place: SessionPlace
  musicUrl: string | null
  tasks: ActiveSessionTask[]
}

export interface UpdateSessionMusicUrlRequest {
  musicUrl: string | null
}
