import { create } from 'zustand'

/**
 * 링크 읽음 상태와 폴더별 링크 변경 신호를 공유한다.
 * 링크 추가·삭제 후 FolderDetail은 변경 신호를 구독해 서버의 정확한 집계 값을 다시 읽는다.
 * readAtById의 null은 아직 읽지 않음을, 키가 없으면 캐시에 없는 링크를 뜻한다.
 */
interface SourceStoreState {
  /** 링크 추가·삭제 후 폴더 상세의 집계 값을 다시 읽기 위한 변경 번호. */
  folderRevisionById: Record<number, number>
  invalidateFolder: (folderId: number) => void
  /** sourceId → 읽은 시각. 아직 읽지 않았으면 null. */
  readAtById: Record<string, string | null>

  /** 목록에서 받아 온 링크를 캐시에 넣는다. 이미 있는 값은 최신 응답으로 덮는다. */
  upsert: (sources: Array<{ sourceId: string; readAt: string | null }>) => void
  setReadAt: (sourceId: string, readAt: string | null) => void
  remove: (sourceIds: string[]) => void
  reset: () => void
}

export const useSourceStore = create<SourceStoreState>((set) => ({
  folderRevisionById: {},
  invalidateFolder: (folderId) => set((current) => ({
    folderRevisionById: {
      ...current.folderRevisionById,
      [folderId]: (current.folderRevisionById[folderId] ?? 0) + 1,
    },
  })),
  readAtById: {},

  upsert: (sources) => set((current) => {
    const readAtById = { ...current.readAtById }
    sources.forEach((source) => {
      readAtById[source.sourceId] = source.readAt
    })
    return { readAtById }
  }),

  setReadAt: (sourceId, readAt) => set((current) => ({
    readAtById: { ...current.readAtById, [sourceId]: readAt },
  })),

  remove: (sourceIds) => set((current) => {
    const removed = new Set(sourceIds)
    const readAtById: Record<string, string | null> = {}
    Object.entries(current.readAtById).forEach(([sourceId, readAt]) => {
      if (!removed.has(sourceId)) readAtById[sourceId] = readAt
    })
    return { readAtById }
  }),

  reset: () => set({ readAtById: {}, folderRevisionById: {} }),
}))
