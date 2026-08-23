import type { SessionDetailPlace, SessionPlace } from '../places/placeTypes'

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

export interface SessionTask {
  id: number
  projectId: number
  projectName: string
  title: string
}

export interface SessionDetailResponse {
  id: number
  type: SessionType
  status: SessionStatus
  plannedDurationSec: number
  actualDurationSec: number | null
  startedAt: string
  endedAt: string | null
  place: SessionDetailPlace
  musicUrl: string | null
  tasks: SessionTask[]
}

export interface UpdateSessionMusicUrlRequest {
  musicUrl: string | null
}

export interface UpdateSessionPlannedDurationRequest {
  plannedDurationSec: number
}
