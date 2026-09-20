
export type FolderStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'ARCHIVED'

export type FolderLoadStatus = 'idle' | 'loading' | 'ready' | 'error'

export interface FolderTag {
  id: number
  name: string
}

export interface FolderTagNameRequest {
  name: string
}

export interface Folder {
  id: number
  name: string
  description: string
  targetDate: string | null
  status: FolderStatus
  tag: FolderTag | null
  /** 이 폴더에 저장한 링크가 하나라도 있는지. 목록 카드의 링크 표시가 사용한다. */
  hasSource: boolean
  /** 이 폴더를 고정한 시각. 고정하지 않았으면 null이다. 목록 정렬이 사용한다. */
  pinnedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface FolderDetail {
  id: number
  name: string
  description: string
  targetDate: string | null
  status: FolderStatus
  tag: FolderTag | null
  pinnedAt: string | null
  sourceCount: number
}

export interface CreateFolderRequest {
  name: string
  description: string
  targetDate: string | null
  tagId: number | null
  newTagName: string | null
}

export interface PinFolderRequest {
  pinned: boolean
}

export interface UpdateFolderRequest {
  name: string
  description: string
  targetDate: string | null
  tagId: number | null
}

export interface UpdateFolderStatusRequest {
  status: FolderStatus
}

export type FolderFieldErrors = Partial<
  Record<keyof CreateFolderRequest | 'status', string>
>

export const folderStatusLabel: Record<FolderStatus, string> = {
  NOT_STARTED: '시작 전',
  IN_PROGRESS: '진행 중',
  ARCHIVED: '보관됨',
}
