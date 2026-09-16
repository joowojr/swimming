import { create } from 'zustand'

/**
 * 역할: 링크의 읽음 여부와 존재 여부를 화면 밖에서도 볼 수 있게 두는 자리.
 *
 * 읽음 처리와 삭제는 LinkFolderView가 자기 목록에서 처리하지만, 같은 폴더의 진척을 함께 세는
 * FolderProgressToast는 그 목록을 볼 수 없다. 그래서 바뀐 값만 여기에 남긴다.
 *
 * 목록의 주인은 여전히 LinkFolderView다. 여기 있는 것은 그 위에 겹쳐 읽는 덮어쓰기이고,
 * taskStore의 byId와 같은 쓰임이다.
 *
 * 키가 없다는 것과 값이 null인 것은 뜻이 다르다. null은 "아직 읽지 않음"이고,
 * 키가 없으면 "이 스토어가 모르는 링크"다. 세는 쪽은 자기가 받아 온 것을 먼저 upsert하므로,
 * 그 뒤에 키가 사라졌다면 지워진 링크다.
 */
interface SourceStoreState {
  /** sourceId → 읽은 시각. 아직 읽지 않았으면 null. */
  readAtById: Record<string, string | null>

  /** 목록·배너가 받아 온 링크를 캐시에 넣는다. 이미 있는 값은 최신 응답으로 덮는다. */
  upsert: (sources: Array<{ sourceId: string; readAt: string | null }>) => void
  setReadAt: (sourceId: string, readAt: string | null) => void
  remove: (sourceIds: string[]) => void
  reset: () => void
}

export const useSourceStore = create<SourceStoreState>((set) => ({
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

  reset: () => set({ readAtById: {} }),
}))
