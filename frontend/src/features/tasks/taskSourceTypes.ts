import type { SourceProcessingStatus } from '../knowledge/knowledgeTypes'

/** 할 일에 연결한 링크 하나. 연결한 순서대로 온다. */
export interface TaskSource {
  sourceId: string
  /** 링크를 저장한 폴더. */
  folderId: number | null
  title: string
  url: string
  status: SourceProcessingStatus
}

export interface AttachTaskSourcesRequest {
  sourceIds: string[]
}
